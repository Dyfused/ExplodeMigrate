package explode.migrate.legacy

import org.bson.codecs.pojo.annotations.BsonId
import java.time.OffsetDateTime

data class MongoRecord(
	@BsonId
	val id: String,
	val playerId: String,
	val chartId: String,
	val score: Int,
	val scoreDetail: ScoreDetail,
	val uploadedTime: OffsetDateTime,
	val RScore: Double?
)

