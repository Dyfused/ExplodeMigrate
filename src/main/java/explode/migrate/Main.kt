@file:JvmName("Main")

package explode.migrate

import explode.migrate.toXtreme.MigrateToX
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import kotlin.coroutines.*
import kotlin.time.*

internal val logger = LoggerFactory.getLogger("Migrate")

const val MongoDbPackage = "org.mongodb.driver"

suspend fun main(args: Array<String>) = runBlocking {
	println("Explode 版本迁移工具 v1 (k2x)")

	if("--debug" in args || "-d" in args) {
		// 增加数据库废话
		System.setProperty("org.slf4j.simpleLogger.log.$MongoDbPackage", "debug")
	} else {
		// 关闭数据库的废话
		System.setProperty("org.slf4j.simpleLogger.log.$MongoDbPackage", "warn")
	}

	when {
		"config" in args -> {
			MigrateToX.configMode()?.migrate() ?: kotlin.run {
				logger.info("请先前往 .config 文件添加启动配置")
			}
		}
		"console" in args -> {
			MigrateToX.consoleMode().migrate()
		}
		else -> {
			logger.info("未指定配置方式，默认使用控制台输入")
			MigrateToX.consoleMode().migrate()
		}
	}
}