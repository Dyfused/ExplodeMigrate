package explode.migrate

import kotlinx.coroutines.reactive.collect
import org.reactivestreams.Publisher

val acceptAll: (String) -> Boolean = { true }

fun getCheckedInputWithDefault(hint: String, validator: (String) -> Boolean = acceptAll, default: String): String {
	return getCheckedInput(hint, validator).ifEmpty { default }
}

fun getCheckedInput(hint: String, validator: (String) -> Boolean = acceptAll): String {
	while(true) {
		print("$hint > ")
		val input = readLine() ?: continue
		if(validator(input)) {
			return input
		}
	}
}

/**
 * 更安全的遍历数据，避免因为某个元素异常导致执行终止
 */
suspend inline fun <T> Publisher<T>.tryCollect(
	exceptionHandler: (Throwable) -> Unit = {},
	action: (T) -> Unit
): PublisherResult<T> {
	var successCount = 0
	val errors = mutableListOf<PublisherExceptionContext<T>>()

	this.collect { el ->
		try {
			action(el)
			successCount++
		} catch(e: Exception) {
			exceptionHandler(e)
			errors.add(PublisherExceptionContext(e, el))
		}
	}

	return PublisherResult(successCount, errors)
}

class PublisherResult<T>(
	val successCount: Int,
	val errors: List<PublisherExceptionContext<T>>
)

data class PublisherExceptionContext<T>(val exception: Throwable, val element: T)

fun <T> PublisherResult<T>.onFinish(action: (PublisherResult<T>) -> Unit) {
	action(this)
}