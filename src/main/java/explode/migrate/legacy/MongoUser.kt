package explode.migrate.legacy

import org.bson.codecs.pojo.annotations.BsonId
import java.time.OffsetDateTime

data class MongoUser(
	@BsonId
	val id: String,
	var username: String,
	var password: String,
	val ownedSets: MutableList<String>,
	val ownedCharts: MutableList<String>,
	var coin: Int,
	var diamond: Int,
	val ppTime: OffsetDateTime,
	val token: String,
	var R: Int,
	val permission: UserPermission,

	var highestGoldenMedal: Int? = null
)
