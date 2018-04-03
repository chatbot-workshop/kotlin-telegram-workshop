/*
 * ChatBot Workshop
 * Copyright (C) 2018 Marcus Fihlon
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package ch.fihlon.workshop.chatbot

import com.google.common.annotations.VisibleForTesting
import org.telegram.abilitybots.api.bot.AbilityBot
import org.telegram.abilitybots.api.db.DBContext
import org.telegram.abilitybots.api.db.MapDBContext
import org.telegram.abilitybots.api.objects.Ability
import org.telegram.abilitybots.api.objects.EndUser
import org.telegram.abilitybots.api.objects.Flag
import org.telegram.abilitybots.api.objects.Locality.ALL
import org.telegram.abilitybots.api.objects.Privacy.PUBLIC
import org.telegram.abilitybots.api.objects.Reply
import org.telegram.abilitybots.api.sender.MessageSender
import org.telegram.abilitybots.api.sender.SilentSender
import org.telegram.abilitybots.api.util.AbilityUtils.getChatId
import org.telegram.telegrambots.ApiContextInitializer
import org.telegram.telegrambots.TelegramBotsApi
import org.telegram.telegrambots.api.methods.GetFile
import org.telegram.telegrambots.api.methods.send.SendMessage
import org.telegram.telegrambots.api.methods.send.SendPhoto
import org.telegram.telegrambots.api.objects.Message
import org.telegram.telegrambots.api.objects.PhotoSize
import org.telegram.telegrambots.api.objects.Update
import org.telegram.telegrambots.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.KeyboardRow
import org.telegram.telegrambots.exceptions.TelegramApiException
import java.io.File
import java.util.function.Consumer
import java.util.function.Predicate


fun main(args: Array<String>) {
    ApiContextInitializer.init()
    val db = MapDBContext.onlineInstance("bot.db")
    val api = TelegramBotsApi()
    val bot = WorkshopBot("1234567890:ABCDEFGHIJKLMNOPQRSTUVWXYZ", "MyWorkshopBot", db)
    api.registerBot(bot)
}

class WorkshopBot(botToken: String, botUsername: String, db: DBContext) : AbilityBot(botToken, botUsername, db) {

    override fun creatorId() = 123456789

    @Suppress("unused")
    fun sayHelloWorld(): Ability {
        return Ability
            .builder()
            .name("hello")
            .info("says hello world")
            .locality(ALL)
            .privacy(PUBLIC)
            .action { context -> silent.send("Hello world", context.chatId()) }
            .build()
    }

    @Suppress("unused")
    fun replyToPhoto() = Reply.of(
            Consumer<Update> { update -> silent.send("Nice pic!", getChatId(update)) },
            Flag.PHOTO)

    private val isMarcus: Predicate<Update>
        get() = Predicate { update -> update.message.from.firstName.equals("Marcus", ignoreCase = true) }

    @Suppress("unused")
    fun sayHi(): Ability {
        return Ability
                .builder()
                .name("hi")
                .info("says hi")
                .locality(ALL)
                .privacy(PUBLIC)
                .action { context ->
                    val firstName = context.user().firstName()
                    silent.send("Hi, $firstName", context.chatId())
                }
                .reply(
                    Consumer<Update> { update -> silent.send("Wow, nice name!", update.message.chatId) },
                    Flag.TEXT,
                    Predicate<Update> { update -> update.message.text.startsWith("/hi") },
                    isMarcus
                )
                .build()
    }

    @Suppress("unused")
    fun counter(): Ability {
        return Ability.builder()
                .name("count")
                .info("increments a counter per user")
                .privacy(PUBLIC)
                .locality(ALL)
                .action { context ->
                    val counterMap = db.getMap<String, Int>("COUNTERS")
                    val userId = context.user().id()
                    val counter = counterMap.compute(userId.toString(), {_, c -> if (c == null) 1 else c + 1})
                    val message = String.format("%s, your count is now %d!",
                            context.user().shortName(), counter)
                    silent.send(message, context.chatId())
                }
                .build()
    }

    @Suppress("unused")
    fun contacts(): Ability {
        return Ability.builder()
                .name("contacts")
                .info("lists all users who contacted this bot")
                .privacy(PUBLIC)
                .locality(ALL)
                .action { context ->
                    val usersMap = db.getMap<String, EndUser>("USERS")
                    val users = usersMap.values.joinToString(", ") { it.username() }
                    val message = "The following users already contacted me: $users"
                    silent.send(message, context.chatId())
                }
                .build()
    }

    @Suppress("unused")
    fun savePhoto(): Reply {
        return Reply.of(
                Consumer<Update> { update ->
                    val photos = update.message.photo
                    val photoSize = photos.stream()
                        .max(Comparator.comparingInt(PhotoSize::getFileSize))
                        .orElse(null)
                    if (photoSize != null) {
                        val filePath = getFilePath(photoSize)
                        val file = downloadPhoto(filePath)
                        println("Temporary file: $file")
                        silent.send("Yeah, I got it!", getChatId(update))
                        sendPhotoFromFileId(photoSize.fileId, getChatId(update))
                    } else {
                        silent.send("Houston, we have a problem!", getChatId(update))
                    }
                },
                Flag.PHOTO)
    }

    private fun getFilePath(photo: PhotoSize): String? {
        if (photo.hasFilePath()) {
            return photo.filePath
        }
        val getFileMethod = GetFile()
        getFileMethod.fileId = photo.fileId
        try {
            val file = execute(getFileMethod)
            return file.filePath
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }

        return null
    }

    private fun downloadPhoto(filePath: String?): File? {
        try {
            return downloadFile(filePath)
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }

        return null
    }

    @Suppress("unused")
    fun sendLogo(): Ability {
        return Ability
                .builder()
                .name("logo")
                .info("send the logo")
                .locality(ALL)
                .privacy(PUBLIC)
                .action { context -> sendPhotoFromUrl("https://www.fihlon.ch/images/logo.png", context.chatId()) }
                .build()
    }

    private fun sendPhotoFromUrl(url: String, chatId: Long?) {
        val sendPhotoRequest = SendPhoto()    // 1
        sendPhotoRequest.setChatId(chatId)    // 2
        sendPhotoRequest.photo = url          // 3
        try {
            sendPhoto(sendPhotoRequest)       // 4
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }
    }

    private fun sendPhotoFromFileId(fileId: String, chatId: Long?) {
        val sendPhotoRequest = SendPhoto()    // 1
        sendPhotoRequest.setChatId(chatId)    // 2
        sendPhotoRequest.photo = fileId       // 3
        try {
            sendPhoto(sendPhotoRequest)       // 4
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }
    }

    @Suppress("unused")
    fun sendIcon(): Ability {
        return Ability
                .builder()
                .name("icon")
                .info("send the icon")
                .locality(ALL)
                .privacy(PUBLIC)
                .action { context -> sendPhotoFromUpload("src/main/resources/chatbot.jpg", context.chatId()) }
                .build()
    }

    private fun sendPhotoFromUpload(filePath: String, chatId: Long?) {
        val sendPhotoRequest = SendPhoto()            // 1
        sendPhotoRequest.setChatId(chatId)            // 2
        sendPhotoRequest.setNewPhoto(File(filePath))  // 3
        try {
            sendPhoto(sendPhotoRequest)               // 4
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }
    }

    @Suppress("unused")
    fun sendKeyboard(): Ability {
        return Ability
                .builder()
                .name("keyboard")
                .info("send a custom keyboard")
                .locality(ALL)
                .privacy(PUBLIC)
                .action { context ->
                    val message = SendMessage()
                    message.setChatId(context.chatId())
                    message.text = "Enjoy this wonderful keyboard!"

                    val keyboardMarkup = ReplyKeyboardMarkup()
                    val keyboard = ArrayList<KeyboardRow>()

                    // row 1
                    var row = KeyboardRow()
                    row.add("/hello")
                    row.add("/hi")
                    row.add("/count")
                    keyboard.add(row)

                    // row 2
                    row = KeyboardRow()
                    row.add("/contacts")
                    row.add("/logo")
                    row.add("/icon")
                    keyboard.add(row)

                    // activate the keyboard
                    keyboardMarkup.keyboard = keyboard
                    message.replyMarkup = keyboardMarkup

                    silent.execute<Message, SendMessage>(message)
                }
                .build()
    }

    @Suppress("unused")
    fun format(): Ability {
        return Ability
                .builder()
                .name("format")
                .info("formats the message")
                .locality(ALL)
                .privacy(PUBLIC)
                .action { context ->
                    silent.sendMd("You can make text *bold* or _italic_.", context.chatId())
                    silent.sendMd("`This is code.`", context.chatId())
                    silent.sendMd("```\nThis\nis\nmulti\nline\ncode.\n```", context.chatId())
                }
                .build()
    }

    @Suppress("unused")
    fun add(): Ability {
        return Ability
                .builder()
                .name("add")
                .info("adds to numbers")
                .locality(ALL)
                .privacy(PUBLIC)
                .input(2)
                .action { context ->
                    val a = Integer.parseInt(context.firstArg())
                    val b = Integer.parseInt(context.secondArg())
                    val sum = a + b
                    silent.send(String.format("The sum of %d and %d is %d", a, b, sum), context.chatId())
                }
                .build()
    }

    @Suppress("unused")
    fun sayNo(): Ability {
        return Ability.builder()
                .name(AbilityBot.DEFAULT)
                .privacy(PUBLIC)
                .locality(ALL)
                .action { context -> silent.send("Sorry, I have no answer for you today.", context.chatId()) }
                .build()
    }

    @VisibleForTesting
    fun setSender(sender: MessageSender) {
        this.sender = sender
    }

    @VisibleForTesting
    fun setSilent(silent: SilentSender) {
        this.silent = silent
    }

}
