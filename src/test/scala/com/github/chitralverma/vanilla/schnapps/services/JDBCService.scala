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

package com.github.chitralverma.vanilla.schnapps.services

import java.sql.ResultSet

import com.github.chitralverma.vanilla.schnapps.ExternalManager
import com.github.chitralverma.vanilla.schnapps.external.jdbc.JDBCExternal
import com.github.chitralverma.vanilla.schnapps.internal.{CustomSubject, RestService}
import javax.ws.rs.Path
import javax.ws.rs.core.Response
import org.apache.shiro.authz.annotation.RequiresAuthentication
import org.apache.shiro.subject.Subject
import org.jboss.resteasy.spi.HttpRequest

import scala.collection.mutable.ArrayBuffer

@Path("/tables")
@RequiresAuthentication
class JDBCService extends RestService with CustomSubject {

  lazy val jdbcLink: JDBCExternal = ExternalManager
    .getExternal[JDBCExternal]("mysql_conn")
    .get

  override def get(request: HttpRequest): Response = {
    val arr: ArrayBuffer[String] = ArrayBuffer.empty[String]

    jdbcLink.executeThis(c => {
      val queryResult: ResultSet = c.prepareStatement("show tables").executeQuery()
      while (queryResult.next()) {
        arr.append(queryResult.getString(1))
      }
    })

    Response.ok().entity(arr.mkString(", ")).build()
  }

  override def getSubject(request: HttpRequest): Subject = {
    val sessionID: String = request.getHttpHeaders.getRequestHeader("sessionID").get(0)
    new Subject.Builder().sessionId(sessionID).buildSubject()
  }

}
