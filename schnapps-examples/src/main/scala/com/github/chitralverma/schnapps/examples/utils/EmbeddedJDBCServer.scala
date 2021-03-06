/*
 *    Copyright 2020 Chitral Verma
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.github.chitralverma.schnapps.examples.utils

import java.sql.{Connection, DriverManager, Statement}

import com.github.chitralverma.schnapps.internal.Logging
import org.hsqldb.persist.HsqlProperties
import org.hsqldb.server.Server

import scala.util._

object EmbeddedJDBCServer extends Logging {

  private var _instance: Server = _

  def start(config: Map[String, Any]): Unit = {
    if (_instance == null) {
      Try(config("jdbcUrl").toString) match {
        case Success(jdbcUrl) =>
          _instance = {

            val props: HsqlProperties = new HsqlProperties()
            props.setProperty("server.database.0", jdbcUrl)

            val hsqldbServer: Server = new Server()
            hsqldbServer.setProperties(props)

            hsqldbServer
          }

          logInfo("Starting Embedded JDBC Server.")
          _instance.start()
          createTestTable(jdbcUrl)
        case Failure(ex) =>
          logError("Invalid value found for config 'jdbcUrl'.", ex)
          throw ex
      }
    } else {
      logWarning("Embedded JDBC Server already running.")
    }
  }

  def createTestTable(jdbcUrl: String): Unit = {
    var con: Connection = null
    var stmt: Statement = null
    var result: Int = 0

    Try {
      // scalastyle:off
      Class.forName("org.hsqldb.jdbc.JDBCDriver")
      // scalastyle:on
      con = DriverManager.getConnection(jdbcUrl, "SA", "")
      stmt = con.createStatement()

      result =
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS test_tbl(name varchar(10), age int)")

    } match {
      case Success(_) => logInfo("Test table created successfully.");
      case Failure(ex) => logError("Failure while creating test table", ex)
    }

  }

  def stop(): Unit = {
    if (!_instance.isNotRunning) {
      logInfo("Stopping Embedded JDBC Server.")
      _instance.stop()
    }
  }

}
