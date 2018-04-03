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

import org.telegram.telegrambots.ApiContextInitializer
import org.telegram.telegrambots.TelegramBotsApi
import org.telegram.telegrambots.api.methods.send.SendMessage
import org.telegram.telegrambots.api.objects.Message
import org.telegram.telegrambots.api.objects.Update
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.exceptions.TelegramApiException

fun main(args: Array<String>) {
    ApiContextInitializer.init()
    val api = TelegramBotsApi()
    val bot = GettingStartedBot()
    api.registerBot(bot)
}

class GettingStartedBot : TelegramLongPollingBot() {

    override fun getBotUsername(): String {
        return "MyWorkshopBot"
    }

    override fun getBotToken(): String {
        return "1234567890:ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    }

    override fun onUpdateReceived(update: Update) {
        if (update.hasMessage() && update.message.hasText()) {
            val chatId = update.message.chatId
            val text = update.message.text
            val message = SendMessage()
                    .setChatId(chatId!!)
                    .setText(text)
            try {
                execute<Message, SendMessage>(message)
            } catch (e: TelegramApiException) {
                e.printStackTrace()
            }
        }
    }
}
