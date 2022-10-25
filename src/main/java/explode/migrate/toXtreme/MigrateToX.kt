package explode.migrate.toXtreme

import com.mongodb.client.model.InsertOneOptions
import com.mongodb.reactivestreams.client.MongoDatabase
import explode.migrate.*
import explode.migrate.legacy.*
import explode2.labyrinth.mongo.po.*
import kotlinx.coroutines.*
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.litote.kmongo.eq
import org.litote.kmongo.reactivestreams.KMongo
import org.litote.kmongo.reactivestreams.getCollectionOfName
import org.slf4j.LoggerFactory
import org.slf4j.MarkerFactory
import java.io.File
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*
import kotlin.time.Duration.Companion.seconds
import explode.migrate.legacy.MongoAssessment as LegacyAssessment
import explode.migrate.legacy.MongoAssessmentGroup as LegacyAssessmentGroup

const val OUTPUT_INTERVAL: Double = 0.5

class MigrateToX(private val old: MongoDatabase, private val new: MongoDatabase) {

	private val logger = LoggerFactory.getLogger("迁移到X")

	suspend fun migrate() {
		logger.info("[1/6] 开始迁移用户数据")
		noException { migrateUser() }

		logger.info("[2/6] 开始迁移曲目数据")
		noException { migrateSets() }

		logger.info("[3/6] 开始迁移谱面数据")
		noException { migrateCharts() }

		logger.info("[4/6] 开始迁移游玩数据")
		noException { migrateRecords() }

		logger.info("[5/6] 开始迁移段位数据")
		noException { migrateAssessments() }

		logger.info("[6/6] 开始迁移段位数据")
		noException { migrateAssessRecords() }

		logger.info("数据转移完成，请检查日志中发生的错误，并加以修复！")
	}

	private suspend fun migrateUser() {
		val marker = MarkerFactory.getMarker("用户数据")

		val oldCol = old.getCollectionOfName<MongoUser>("User")
		val newCol = new.getCollectionOfName<MongoGameUser>("Users")

		logger.info(marker, "源数据库：${oldCol.namespace}")
		logger.info(marker, "目标数据库：${newCol.namespace}")

		newCol.drop().awaitFirstOrNull()

		val totalCount = oldCol.countDocuments().awaitFirst()
		var processedCount = 0

		coroutineScope {
			// 用来显示进度的协程
			val outputJob = launch {
				while(true) {
					delay(OUTPUT_INTERVAL.seconds)
					val now = processedCount.toDouble() / totalCount
					logger.info(marker, "处理进度 %.2f%% - %s".format(now * 100, processedCount))
				}
			}

			launch {
				logger.info(marker, "开始转换数据")
				oldCol.find().tryCollect { u ->
					logger.debug(marker, "原始数据 $u")

					processedCount++

					val newUser = MongoGameUser(
						u.id, u.username, u.password,
						u.coin, u.diamond, u.ppTime,
						u.permission.review, u.ownedSets
					)

					logger.debug(marker, "新结构数据 $newUser")

					newCol.insertOne(newUser, InsertOneOptions().comment("来自旧数据结构更新（Migrate）"))
						.awaitFirstOrNull()

//					logger.info(marker, "${newUser.username}(${newUser.id})")
				}.onFinish {
					// 取消显示协程
					outputJob.cancel()
					logger.info(marker, "成功转换用户数 ${it.successCount} ，失败 ${it.errors.size}")
					it.errors.forEach { (ex, el) ->
						logger.warn(
							marker,
							"错误用户 ${el.username}(${el.id})，失败原因：${ex.javaClass.simpleName}(${ex.message})"
						)
					}
				}
			}
		}
	}

	private suspend fun migrateSets() {
		val marker = MarkerFactory.getMarker("曲目数据")

		val oldCol = old.getCollectionOfName<MongoSet>("ChartSet")
		val newCol = new.getCollectionOfName<MongoSongSet>("Sets")

		val userCol = new.getCollectionOfName<MongoGameUser>("Users")

		val uploadTimePlaceholder = OffsetDateTime.of(2022, 1, 1, 0, 0, 0, 0, ZoneOffset.ofHours(8))

		logger.info(marker, "源数据库：${oldCol.namespace}")
		logger.info(marker, "目标数据库：${newCol.namespace}")

		newCol.drop().awaitFirstOrNull()

		val totalCount = oldCol.countDocuments().awaitFirst()
		var processedCount = 0

		coroutineScope {
			// 用来显示进度的协程
			val outputJob = launch {
				while(true) {
					delay(OUTPUT_INTERVAL.seconds)
					val now = processedCount.toDouble() / totalCount
					logger.info(marker, "处理进度 %.2f%% - %s".format(now * 100, processedCount))
				}
			}

			launch {
				logger.info(marker, "开始转换数据")
				oldCol.find().tryCollect { s ->
					logger.debug(marker, "原始数据 $s")

					processedCount++

					@Suppress("DEPRECATION")
					val new = MongoSongSet(
						s.id,
						s.musicName,
						s.composerName,
						s.introduction.orEmpty(),
						s.price,
						s.noterDisplayOverride ?: kotlin.run {
							userCol.find(MongoGameUser::id eq s.noterId).awaitFirstOrNull()?.username ?: kotlin.run {
								logger.warn(
									marker,
									"曲目 ${s.musicName}(${s.id}) 谱师信息丢失，无法找到用户 ${s.noterId}"
								)
								"unknown"
							}
						},
						s.noterId,
						s.charts,
						s.uploadedTime ?: uploadTimePlaceholder,
						when(s.status) {
							SetStatus.UNRANKED -> 0
							SetStatus.RANKED -> 1
							SetStatus.OFFICIAL -> 2
							SetStatus.NEED_REVIEW -> 0
							SetStatus.HIDDEN -> 0
						},
						hidden = s.status == SetStatus.HIDDEN || s.isHidden,
						reviewing = s.status == SetStatus.NEED_REVIEW || s.isReviewing
					)

					logger.debug(marker, "新结构数据 $new")

					newCol.insertOne(new, InsertOneOptions().comment("来自旧数据结构更新（Migrate）")).awaitFirstOrNull()

//					logger.info(marker, "${new.musicName} - ${new.noterName}(${new.id})")
				}.onFinish {
					outputJob.cancel()
					logger.info(marker, "成功转换曲目数 ${it.successCount} ，失败 ${it.errors.size}")
					it.errors.forEach { (ex, el) ->
						logger.warn(
							marker,
							"错误曲目 ${el.musicName}(${el.id})，失败原因：${ex.javaClass.simpleName}(${ex.message})"
						)
					}
				}
			}
		}
	}

	private suspend fun migrateCharts() {
		val marker = MarkerFactory.getMarker("谱面数据")

		val oldCol = old.getCollectionOfName<MongoChart>("Chart")
		val newCol = new.getCollectionOfName<MongoSongChart>("Charts")

		logger.info(marker, "源数据库：${oldCol.namespace}")
		logger.info(marker, "目标数据库：${newCol.namespace}")

		newCol.drop().awaitFirstOrNull()

		val totalCount = oldCol.countDocuments().awaitFirst()
		var processedCount = 0

		coroutineScope {
			// 用来显示进度的协程
			val outputJob = launch {
				while(true) {
					delay(OUTPUT_INTERVAL.seconds)
					val now = processedCount.toDouble() / totalCount
					logger.info(marker, "处理进度 %.2f%% - %s".format(now * 100, processedCount))
				}
			}

			launch {
				logger.info(marker, "开始转换数据")
				oldCol.find().tryCollect { c ->
					logger.debug(marker, "原始数据 $c")

					processedCount++

					val new = MongoSongChart(c.id, c.difficultyClass, c.difficultyValue, c.D)

					logger.debug(marker, "新结构数据 $new")

					newCol.insertOne(new, InsertOneOptions().comment("来自旧数据结构更新（Migrate）")).awaitFirstOrNull()

//					logger.info(
//						marker, "${new.id} - ${
//							when(new.difficultyClass) {
//								1 -> "Casual"
//								2 -> "Normal"
//								3 -> "Hard"
//								4 -> "Mega"
//								5 -> "Giga"
//								6 -> "Tera"
//								else -> "????"
//							}
//						}${new.difficultyValue}"
//					)
				}.onFinish {
					outputJob.cancel()
					logger.info(marker, "成功转换曲目数 ${it.successCount} ，失败 ${it.errors.size}")
					it.errors.forEach { (ex, el) ->
						logger.warn(
							marker,
							"错误谱面 ${el.id}，失败原因：${ex.javaClass.simpleName}(${ex.message})"
						)
					}
				}
			}
		}
	}

	private suspend fun migrateRecords() {
		val marker = MarkerFactory.getMarker("游玩数据")

		val oldCol = old.getCollectionOfName<MongoRecord>("PlayRecord")
		val newCol = new.getCollectionOfName<MongoGameRecord>("GameRecords")

		logger.info(marker, "源数据库：${oldCol.namespace}")
		logger.info(marker, "目标数据库：${newCol.namespace}")

		newCol.drop().awaitFirstOrNull()

		val totalCount = oldCol.countDocuments().awaitFirst()
		var processedCount = 0

		coroutineScope {
			// 用来显示进度的协程
			val outputJob = launch {
				while(true) {
					delay(OUTPUT_INTERVAL.seconds)
					val now = processedCount.toDouble() / totalCount
					logger.info(marker, "处理进度 %.2f%% - %s".format(now * 100, processedCount))
				}
			}

			launch {
				logger.info(marker, "开始转换数据")
				oldCol.find().tryCollect { s ->
					logger.debug(marker, "原始数据 $s")

					processedCount++

					val new = MongoGameRecord(
						s.id,
						s.playerId,
						s.chartId,
						s.score,
						RecordDetail(s.scoreDetail.perfect, s.scoreDetail.good, s.scoreDetail.miss),
						s.uploadedTime,
						s.RScore?.toInt()
					)

					logger.debug(marker, "新结构数据 $new")

					newCol.insertOne(new, InsertOneOptions().comment("来自旧数据结构更新（Migrate）")).awaitFirstOrNull()
				}.onFinish {
					// 完成后取消协程
					outputJob.cancel()
					logger.info(marker, "成功转换曲目数 ${it.successCount} ，失败 ${it.errors.size}")
					it.errors.forEach { (ex, el) ->
						logger.warn(
							marker,
							"错误谱面 ${el.id}，失败原因：${ex.javaClass.simpleName}(${ex.message})"
						)
					}
				}
			}
		}
	}

	private suspend fun migrateAssessments() {
		val marker = MarkerFactory.getMarker("段位数据")

		val oldGroupCol = old.getCollectionOfName<LegacyAssessmentGroup>("AssessmentGroup")
		val oldAssessCol = old.getCollectionOfName<LegacyAssessment>("Assessment")
		val newCol = new.getCollectionOfName<MongoAssessGroup>("AssessInfo")

		logger.info(marker, "源数据库：${oldGroupCol.namespace} 和 ${oldAssessCol.namespace}")
		logger.info(marker, "目标数据库：${newCol.namespace}")

		newCol.drop().awaitFirstOrNull()

		val groupCount = oldGroupCol.countDocuments().awaitFirst()

		logger.info(marker, "开始转换数据")
		oldGroupCol.find().tryCollect { oldGroup ->
			logger.debug(marker, "原始段位组数据 $oldGroup")

			val assessments = oldGroup.assessments.map { (level, aid) ->
				val oldAss = oldAssessCol.find(LegacyAssessment::id eq aid).awaitFirst()
				logger.debug(marker, "原始段位数据 $oldAss")
				val newAss = MongoAssessment(
					oldAss.id,
					level,
					oldAss.lifeBarLength,
					oldAss.normalPassAcc,
					oldAss.goldenPassAcc,
					oldAss.exMiss,
					oldAss.charts
				)
				logger.debug(marker, "新结段位构数据 $newAss")
				newAss
			}

			val new = MongoAssessGroup(oldGroup.id, oldGroup.name, groupCount == 1L, assessments)

			logger.debug(marker, "新段位组数据 $new")

			newCol.insertOne(new, InsertOneOptions().comment("来自旧数据结构更新（Migrate）")).awaitFirstOrNull()
		}.onFinish {
			logger.info(marker, "成功转换段位数 ${it.successCount} ，失败 ${it.errors.size}")
			if(groupCount != 1L) {
				logger.warn(marker, "请手动选择激活的段位组，将 selected 字段设置为 true")
			}
			it.errors.forEach { (ex, el) ->
				logger.warn(
					marker,
					"错误段位 ${el.id}，失败原因：${ex.javaClass.simpleName}(${ex.message})"
				)
			}
		}
	}

	private suspend fun migrateAssessRecords() {
		val marker = MarkerFactory.getMarker("段位游玩数据")

		val oldCol = old.getCollectionOfName<MongoAssessmentRecord>("AssessmentRecord")
		val newCol = new.getCollectionOfName<MongoAssessRecord>("AssessRecords")

		logger.info(marker, "源数据库：${oldCol.namespace}")
		logger.info(marker, "目标数据库：${newCol.namespace}")

		newCol.drop().awaitFirstOrNull()

		val totalCount = oldCol.countDocuments().awaitFirst()
		var processedCount = 0

		coroutineScope {
			// 用来显示进度的协程
			val outputJob = launch {
				while(true) {
					delay(OUTPUT_INTERVAL.seconds)
					val now = processedCount.toDouble() / totalCount
					logger.info(marker, "处理进度 %.2f%% - %s".format(now * 100, processedCount))
				}
			}

			launch {
				logger.info(marker, "开始转换数据")
				oldCol.find().tryCollect { s ->
					logger.debug(marker, "原始数据 $s")

					processedCount++

					val new = MongoAssessRecord(
						s.id,
						s.playerId,
						s.assessmentId,
						s.records.map {
							AssessmentRecordDetail(
								it.scoreDetail.perfect,
								it.scoreDetail.good,
								it.scoreDetail.miss,
								it.score,
								it.accuracy
							)
						},
						s.exRecord?.let {
							AssessmentRecordDetail(
								it.scoreDetail.perfect,
								it.scoreDetail.good,
								it.scoreDetail.miss,
								it.score,
								it.accuracy
							)
						},
						s.time,
					)

					logger.debug(marker, "新结构数据 $new")

					newCol.insertOne(new, InsertOneOptions().comment("来自旧数据结构更新（Migrate）")).awaitFirstOrNull()
				}.onFinish {
					// 完成后取消协程
					outputJob.cancel()
					logger.info(marker, "成功转换成绩数 ${it.successCount} ，失败 ${it.errors.size}")
					it.errors.forEach { (ex, el) ->
						logger.warn(
							marker,
							"错误成绩 ${el.id}，失败原因：${ex.javaClass.simpleName}(${ex.message})"
						)
					}
				}
			}
		}
	}

	companion object {
		fun consoleMode(): MigrateToX {
			while(true) {
				val cli =
					KMongo.createClient(getCheckedInputWithDefault("数据库地址", default = "mongodb://localhost:27017"))
				val old = cli.getDatabase(getCheckedInputWithDefault("旧数据库名称", default = "ExplodeOld"))
				val new = cli.getDatabase(getCheckedInputWithDefault("新数据库名称", default = "ExplodeMigrate"))

				logger.info("数据将会从 ${old.name} 转移到 ${new.name} 中，${new.name} 里原有的数据将会被直接丢弃，你确定要这样做么？（输入：Yes）")
				val confirmStr =
					getCheckedInput("[Yes/No/Quit]") {
						it.lowercase() == "yes" || it.lowercase() == "no" || it.lowercase() == "quit"
					}.lowercase()
				if(confirmStr == "yes") {
					logger.info("开始转移数据，请不要强制退出程序！")
					return MigrateToX(old, new)
				} else if(confirmStr == "quit") {
					throw GoodnightException
				}
			}
		}

		fun configMode(): MigrateToX? {
			val file = File(".config")
			val created = file.createNewFile()

			val prop = Properties().also { it.load(file.bufferedReader()) }

			// 设置默认值
			prop.putIfAbsent("MongoDBUrl", "mongodb://localhost:27017")
			prop.putIfAbsent("SourceDatabaseName", "ExplodeOld")
			prop.putIfAbsent("DestinationDatabaseName", "ExplodeMigrated")

			prop.store(file.bufferedWriter(), "Edited by MigrateX")

			val cli = KMongo.createClient(prop.getProperty("MongoDBUrl"))
			val old = cli.getDatabase(prop.getProperty("SourceDatabaseName"))
			val new = cli.getDatabase(prop.getProperty("DestinationDatabaseName"))

			return if(!created) MigrateToX(old, new) else null
		}
	}
}

/**
 * 整蛊用的异常，用 `quit` 退出时抛出
 */
object GoodnightException : Exception("Goodnight!") {
	init {
		stackTrace =
			arrayOf(
				StackTraceElement(
					"Dyfused_Comittee",
					"Explode", "unstable",
					"Dynamite", "explode",
					"synthetic", 0)
			)
	}
}