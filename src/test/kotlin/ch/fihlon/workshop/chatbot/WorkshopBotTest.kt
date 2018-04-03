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

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.telegram.abilitybots.api.db.DBContext
import org.telegram.abilitybots.api.db.MapDBContext
import org.telegram.abilitybots.api.objects.EndUser
import org.telegram.abilitybots.api.objects.MessageContext
import org.telegram.abilitybots.api.sender.MessageSender
import org.telegram.abilitybots.api.sender.SilentSender
import org.telegram.telegrambots.api.methods.send.SendMessage
import org.telegram.telegrambots.api.objects.Message
import org.telegram.telegrambots.api.objects.Update
import org.telegram.telegrambots.exceptions.TelegramApiException

class WorkshopBotTest {

    companion object {
        private const val BOT_TOKEN = "1234567890:ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        private const val BOT_USERNAME = "MyWorkshopBot"

        private const val USER_ID = 1337
        private const val CHAT_ID = 1337L
    }

    private var bot: WorkshopBot? = null
    private var db: DBContext? = null
    private var sender: MessageSender? = null

    @Before
    fun setUp() {
        // Offline instance will get deleted at JVM shutdown
        db = MapDBContext.offlineInstance("test")
        bot = WorkshopBot(BOT_TOKEN, BOT_USERNAME, db!!)
        sender = mock(MessageSender::class.java)
        bot!!.setSender(sender!!)
        bot!!.setSilent(SilentSender(sender))
    }

    @Test
    @Throws(TelegramApiException::class)
    fun sayHelloWorld() {
        val mockedUpdate = mock(Update::class.java)
        val endUser = EndUser.endUser(USER_ID, "Foo", "Bar", "foobar42")
        val context = MessageContext.newContext(mockedUpdate, endUser, CHAT_ID)

        bot!!.sayHelloWorld().action().accept(context)

        val message = SendMessage()
        message.setChatId(CHAT_ID)
        message.text = "Hello world"
        verify<MessageSender>(sender, times(1)).execute<Message, SendMessage>(message)
    }

    @After
    fun tearDown() {
        db!!.clear()
    }
}
