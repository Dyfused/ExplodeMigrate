package explode.migrate.legacy

import org.bson.codecs.pojo.annotations.BsonId
import java.time.OffsetDateTime

data class MongoSet(
	@BsonId
	val id: String,
	var musicName: String,
	var composerName: String,
	var noterId: String,
	var introduction: String?,
	var price: Int,
	var status: SetStatus,
	val charts: MutableList<String>,

	var noterDisplayOverride: String? = null,
	val uploadedTime: OffsetDateTime? = null,

	var isHidden: Boolean = false,
	var isReviewing: Boolean = false,
)