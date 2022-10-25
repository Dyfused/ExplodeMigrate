package explode.migrate.legacy

import org.bson.codecs.pojo.annotations.BsonId

data class MongoChart(
	@BsonId
	val id: String,
	val difficultyClass: Int,
	val difficultyValue: Int,
	var D: Double?
)