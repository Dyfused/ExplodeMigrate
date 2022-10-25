package explode.migrate.legacy

import org.bson.codecs.pojo.annotations.BsonId
import java.time.OffsetDateTime

data class MongoAssessmentRecord(

	val playerId: String,
	val assessmentId: String,

	val result: Int, // 0-Failed; 1-Success; 2-Ex Success

	val records: List<MongoAssessmentRecordEntry>,
	val exRecord: MongoAssessmentRecordEntry?,

	val totalScore: Int,
	val accuracy: Double,

	val time: OffsetDateTime,

	@BsonId
	val id: String
)

data class MongoAssessmentRecordEntry(
	val score: Int,
	val scoreDetail: ScoreDetail,
	val accuracy: Double
)

