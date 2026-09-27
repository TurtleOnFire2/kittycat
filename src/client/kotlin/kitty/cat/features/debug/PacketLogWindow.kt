package kitty.cat.features.debug

import net.minecraft.network.protocol.Packet
import org.reflections.Reflections
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.lang.reflect.Modifier
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.IdentityHashMap
import java.util.Optional
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.table.DefaultTableModel

object PacketLogWindow {
    init {
        // Fabric starts Minecraft in headless AWT mode even though the client has a
        // graphical desktop. Swing needs this cleared before its toolkit is initialized.
        System.setProperty("java.awt.headless", "false")
    }

    const val ALL_PACKETS = "All packets"
    private const val MAX_LOGGED_PACKETS = 10_000
    private val logger = LoggerFactory.getLogger("Kittycat Packet Log")
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    enum class Direction(val label: String) {
        INBOUND("Inbound"), OUTBOUND("Outbound")
    }

    data class Entry(val time: String, val direction: Direction, val packetName: String, val values: String)

    val packetOptions: List<String> by lazy {
        val discovered = runCatching {
            Reflections("net.minecraft.network.protocol")
                .getSubTypesOf(Packet::class.java)
                .asSequence()
                .filterNot { it.isInterface || Modifier.isAbstract(it.modifiers) }
                .map { it.simpleName }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
                .toList()
        }.getOrDefault(emptyList())
        listOf(ALL_PACKETS) + discovered
    }

    private val entries = ArrayDeque<Entry>()
    private var frame: JFrame? = null
    private var model: DefaultTableModel? = null

    fun log(packet: Packet<*>, direction: Direction) {
        val entry = Entry(
            LocalTime.now().format(timeFormat),
            direction,
            packet.javaClass.simpleName,
            describe(packet)
        )
        SwingUtilities.invokeLater {
            entries.addLast(entry)
            model?.addRow(arrayOf(entry.time, entry.direction.label, entry.packetName))
            while (entries.size > MAX_LOGGED_PACKETS) {
                entries.removeFirst()
                if ((model?.rowCount ?: 0) > 0) model?.removeRow(0)
            }
        }
    }

    fun open() = SwingUtilities.invokeLater {
        try {
            frame?.let {
                it.isVisible = true
                it.extendedState = JFrame.NORMAL
                it.toFront()
                it.requestFocus()
                return@invokeLater
            }

            val tableModel = object : DefaultTableModel(arrayOf("Time", "Direction", "Packet"), 0) {
                override fun isCellEditable(row: Int, column: Int) = false
            }
            entries.forEach { tableModel.addRow(arrayOf(it.time, it.direction.label, it.packetName)) }
            val table = JTable(tableModel).apply {
            autoCreateRowSorter = true
            fillsViewportHeight = true
            columnModel.getColumn(0).preferredWidth = 110
            columnModel.getColumn(1).preferredWidth = 80
            columnModel.getColumn(2).preferredWidth = 500
            addMouseListener(object : MouseAdapter() {
                override fun mousePressed(event: MouseEvent) = showDetailsOnRightClick(event)
                override fun mouseReleased(event: MouseEvent) = showDetailsOnRightClick(event)

                private fun showDetailsOnRightClick(event: MouseEvent) {
                    if (!event.isPopupTrigger) return
                    val viewRow = rowAtPoint(event.point)
                    if (viewRow < 0) return
                    setRowSelectionInterval(viewRow, viewRow)
                    val entry = entries.getOrNull(convertRowIndexToModel(viewRow)) ?: return
                    showDetails(entry)
                }
            })
        }
            val clearButton = JButton("Clear").apply { addActionListener { clear() } }
            val footer = JPanel(BorderLayout()).apply { add(clearButton, BorderLayout.EAST) }

            model = tableModel
            frame = JFrame("Kittycat Packet Log").apply {
                defaultCloseOperation = JFrame.HIDE_ON_CLOSE
                layout = BorderLayout()
                add(JScrollPane(table), BorderLayout.CENTER)
                add(footer, BorderLayout.SOUTH)
                minimumSize = Dimension(640, 360)
                size = Dimension(900, 600)
                setLocationByPlatform(true)
                isVisible = true
                toFront()
                requestFocus()
            }
        } catch (error: Throwable) {
            logger.error("Could not open the separate packet log window", error)
        }
    }

    fun close() = SwingUtilities.invokeLater { frame?.isVisible = false }

    fun clear() = SwingUtilities.invokeLater {
        entries.clear()
        model?.rowCount = 0
    }

    private fun showDetails(entry: Entry) {
        val area = JTextArea(entry.values).apply {
            isEditable = false
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            caretPosition = 0
        }
        JDialog(frame, "${entry.direction.label}: ${entry.packetName}", false).apply {
            layout = BorderLayout()
            add(JScrollPane(area), BorderLayout.CENTER)
            size = Dimension(760, 600)
            setLocationRelativeTo(frame)
            isVisible = true
        }
    }

    private fun describe(value: Any): String = buildString {
        append(value.javaClass.name).append('\n')
        appendValue(value, this, "", Collections.newSetFromMap(IdentityHashMap()), 0)
    }

    private fun appendValue(value: Any?, output: StringBuilder, indent: String, seen: MutableSet<Any>, depth: Int) {
        if (value == null) {
            output.append("null\n")
            return
        }
        if (isSimple(value)) {
            output.append(value).append('\n')
            return
        }
        if (depth >= 10) {
            output.append(value).append(" (maximum depth reached)\n")
            return
        }
        if (!seen.add(value)) {
            output.append("<already shown>\n")
            return
        }

        when (value) {
            is Optional<*> -> {
                if (value.isPresent) appendValue(value.get(), output, indent, seen, depth + 1)
                else output.append("empty\n")
            }
            is Map<*, *> -> value.entries.forEachIndexed { index, entry ->
                output.append(indent).append('[').append(index).append("] key = ")
                appendValue(entry.key, output, "$indent  ", seen, depth + 1)
                output.append(indent).append("    value = ")
                appendValue(entry.value, output, "$indent      ", seen, depth + 1)
            }
            is Iterable<*> -> value.forEachIndexed { index, item ->
                output.append(indent).append('[').append(index).append("] = ")
                appendValue(item, output, "$indent  ", seen, depth + 1)
            }
            else -> {
                if (value.javaClass.isArray) {
                    val length = java.lang.reflect.Array.getLength(value)
                    repeat(length) { index ->
                        output.append(indent).append('[').append(index).append("] = ")
                        appendValue(java.lang.reflect.Array.get(value, index), output, "$indent  ", seen, depth + 1)
                    }
                } else if (shouldInspectFields(value.javaClass)) {
                    fieldsOf(value.javaClass).forEach { field ->
                        output.append(indent).append(field.name).append(" = ")
                        val fieldValue = runCatching {
                            field.trySetAccessible()
                            field.get(value)
                        }.getOrElse { "<unavailable: ${it.javaClass.simpleName}>" }
                        appendValue(fieldValue, output, "$indent  ", seen, depth + 1)
                    }
                } else {
                    output.append(value).append('\n')
                }
            }
        }
    }

    private fun fieldsOf(type: Class<*>): List<java.lang.reflect.Field> = generateSequence(type) { it.superclass }
        .takeWhile { it != Any::class.java }
        .flatMap { it.declaredFields.asSequence() }
        .filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic }
        .toList()

    private fun shouldInspectFields(type: Class<*>): Boolean =
        type.name.startsWith("net.minecraft.") || type.name.startsWith("com.mojang.")

    private fun isSimple(value: Any): Boolean = value is String || value is Number || value is Boolean ||
        value is Char || value is Enum<*> || value.javaClass.isPrimitive
}
