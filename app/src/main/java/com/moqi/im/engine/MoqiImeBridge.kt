package com.moqi.im.engine

import android.view.KeyEvent
import android.util.Log
import mobilebridge.MobileResponse
import mobilebridge.Mobilebridge
import mobilebridge.Session

data class MoqiImeResult(
    val success: Boolean,
    val handled: Boolean,
    val composition: String,
    val commit: String,
    val candidates: List<String>,
    val showCandidates: Boolean,
    val error: String
)

class MoqiImeSession(
    private val guid: String = Mobilebridge.GUIDMoqi,
    private val androidDataDir: String? = null
) {
    private var session: Session? = null
    private var initError: String = ""

    init {
        runCatching {
            androidDataDir?.let {
                Mobilebridge.setAndroidDataDir(it)
                Log.i(TAG, "android data dir configured path=$it")
            }
            val created = Mobilebridge.newSession(guid)
            session = created
            val initResp = created.init()
            if (initResp?.success != true) {
                initError = initResp?.error.orEmpty().ifBlank { "moqi-ime init failed" }
                Log.e(TAG, "init failed guid=$guid error=$initError")
                return@runCatching
            }
            Log.i(TAG, "init success guid=$guid")
            val activateResp = created.activate()
            if (activateResp?.success != true) {
                initError = activateResp?.error.orEmpty().ifBlank { "moqi-ime activate failed" }
                Log.e(TAG, "activate failed guid=$guid error=$initError")
            } else {
                Log.i(TAG, "activate success guid=$guid candidates=${activateResp.candidateList?.len() ?: 0}")
            }
        }.onFailure { error ->
            initError = error.message.orEmpty().ifBlank { error::class.java.simpleName }
            Log.e(TAG, "session create failed guid=$guid", error)
            session = null
        }
    }

    fun keyDown(keyCode: Int, charCode: Int = 0): MoqiImeResult {
        val activeSession = session ?: return initErrorResult()
        return runCatching {
            activeSession.keyDown(keyCode.toLong(), charCode.toLong()).toResult()
        }.getOrElse { error ->
            Log.e(TAG, "keyDown failed keyCode=$keyCode charCode=$charCode", error)
            error.toResult()
        }
    }

    fun selectCandidate(index: Int): MoqiImeResult {
        val activeSession = session ?: return initErrorResult()
        return runCatching {
            activeSession.selectCandidate(index.toLong()).toResult()
        }.getOrElse { error ->
            Log.e(TAG, "selectCandidate failed index=$index", error)
            error.toResult()
        }
    }

    fun command(commandId: Int): MoqiImeResult {
        val activeSession = session ?: return initErrorResult()
        return runCatching {
            activeSession.command(commandId.toLong()).toResult()
        }.getOrElse { error ->
            Log.e(TAG, "command failed commandId=$commandId", error)
            error.toResult()
        }
    }

    fun reset(): MoqiImeResult {
        return keyDown(MoqiImeKeyMapper.VK_ESCAPE)
    }

    fun close() {
        runCatching {
            session?.close()
        }.onFailure { error ->
            Log.w(TAG, "close failed", error)
        }
        session = null
    }

    private fun initErrorResult(): MoqiImeResult {
        return MoqiImeResult(
            success = false,
            handled = false,
            composition = "",
            commit = "",
            candidates = emptyList(),
            showCandidates = false,
            error = initError.ifBlank { "moqi-ime session is not initialized" }
        )
    }

    companion object {
        private const val TAG = "MoqiImeSession"
    }
}

object MoqiImeKeyMapper {
    const val VK_BACK = 0x08
    const val VK_RETURN = 0x0D
    const val VK_ESCAPE = 0x1B
    const val VK_SPACE = 0x20
    const val VK_OEM_1 = 0xBA
    const val VK_OEM_PERIOD = 0xBE

    fun fromAndroidKeyCode(keyCode: Int, shifted: Boolean): Pair<Int, Int>? {
        return when (keyCode) {
            KeyEvent.KEYCODE_A -> printable('a', shifted)
            KeyEvent.KEYCODE_B -> printable('b', shifted)
            KeyEvent.KEYCODE_C -> printable('c', shifted)
            KeyEvent.KEYCODE_D -> printable('d', shifted)
            KeyEvent.KEYCODE_E -> printable('e', shifted)
            KeyEvent.KEYCODE_F -> printable('f', shifted)
            KeyEvent.KEYCODE_G -> printable('g', shifted)
            KeyEvent.KEYCODE_H -> printable('h', shifted)
            KeyEvent.KEYCODE_I -> printable('i', shifted)
            KeyEvent.KEYCODE_J -> printable('j', shifted)
            KeyEvent.KEYCODE_K -> printable('k', shifted)
            KeyEvent.KEYCODE_L -> printable('l', shifted)
            KeyEvent.KEYCODE_M -> printable('m', shifted)
            KeyEvent.KEYCODE_N -> printable('n', shifted)
            KeyEvent.KEYCODE_O -> printable('o', shifted)
            KeyEvent.KEYCODE_P -> printable('p', shifted)
            KeyEvent.KEYCODE_Q -> printable('q', shifted)
            KeyEvent.KEYCODE_R -> printable('r', shifted)
            KeyEvent.KEYCODE_S -> printable('s', shifted)
            KeyEvent.KEYCODE_T -> printable('t', shifted)
            KeyEvent.KEYCODE_U -> printable('u', shifted)
            KeyEvent.KEYCODE_V -> printable('v', shifted)
            KeyEvent.KEYCODE_W -> printable('w', shifted)
            KeyEvent.KEYCODE_X -> printable('x', shifted)
            KeyEvent.KEYCODE_Y -> printable('y', shifted)
            KeyEvent.KEYCODE_Z -> printable('z', shifted)
            KeyEvent.KEYCODE_0 -> digit('0')
            KeyEvent.KEYCODE_1 -> digit('1')
            KeyEvent.KEYCODE_2 -> digit('2')
            KeyEvent.KEYCODE_3 -> digit('3')
            KeyEvent.KEYCODE_4 -> digit('4')
            KeyEvent.KEYCODE_5 -> digit('5')
            KeyEvent.KEYCODE_6 -> digit('6')
            KeyEvent.KEYCODE_7 -> digit('7')
            KeyEvent.KEYCODE_8 -> digit('8')
            KeyEvent.KEYCODE_9 -> digit('9')
            else -> null
        }
    }

    fun printable(ch: Char, shifted: Boolean = false): Pair<Int, Int> {
        val out = if (shifted) ch.uppercaseChar() else ch
        return out.code to out.code
    }

    fun digit(ch: Char): Pair<Int, Int> = ch.code to ch.code
}

private fun MobileResponse?.toResult(): MoqiImeResult {
    if (this == null) {
        return MoqiImeResult(
            success = false,
            handled = false,
            composition = "",
            commit = "",
            candidates = emptyList(),
            showCandidates = false,
            error = "empty moqi-ime response"
        )
    }

    val list = candidateList
    val candidates = if (list == null) {
        emptyList()
    } else {
        (0 until list.len()).map { index -> list.get(index) }
    }

    return MoqiImeResult(
        success = success,
        handled = returnValue != 0L,
        composition = compositionString.orEmpty(),
        commit = commitString.orEmpty(),
        candidates = candidates,
        showCandidates = showCandidates,
        error = error.orEmpty()
    )
}

private fun Throwable.toResult(): MoqiImeResult {
    return MoqiImeResult(
        success = false,
        handled = false,
        composition = "",
        commit = "",
        candidates = emptyList(),
        showCandidates = false,
        error = message.orEmpty().ifBlank { javaClass.simpleName }
    )
}
