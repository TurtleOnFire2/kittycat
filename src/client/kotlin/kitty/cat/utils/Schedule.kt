package kitty.cat.utils

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents

object Schedule {
    data class Task(var ticks: Int, val callback: () -> Unit)

    private val scheduledClient = mutableListOf<Task>()
    private val scheduledServer = mutableListOf<Task>()

    fun register() {
        ClientTickEvents.START_CLIENT_TICK.register { tickClient() }
    }

    fun schedule(ticks: Int, server: Boolean = false, callback: () -> Unit = {}) {
        if (ticks < 1) {
            callback.invoke()
            return
        }
        if (server) {
            scheduledServer.add(Task(ticks, callback))
        } else {
            scheduledClient.add(Task(ticks, callback))
        }
    }

    private fun tick(list: MutableList<Task>) {
        val due = mutableListOf<Task>()
        val iter = list.iterator()
        while (iter.hasNext()) {
            val t = iter.next()
            if (--t.ticks <= 0) {
                due += t
                iter.remove()
            }
        }
        for (t in due) {
            try {
                t.callback()
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    fun tickClient() = tick(scheduledClient)
    fun tickServer() = tick(scheduledServer)
}