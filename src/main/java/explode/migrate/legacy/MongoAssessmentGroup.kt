package explode.migrate.legacy

import org.bson.codecs.pojo.annotations.BsonId

data class MongoAssessmentGroup(
	var name: String,
	val assessments: Map<Int, String>, // medalLevel to Assessment

	@BsonId
	val id: String
)
