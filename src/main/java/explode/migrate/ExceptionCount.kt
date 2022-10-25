package explode.migrate

import java.lang.Exception

inline fun <T, R> T.noException(block: T.() -> R): R? {
	return try {
		this.block()
	} catch(e: Exception) {
		e.printStackTrace()
		null
	}
}