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

package com.github.chitralverma.schnapps.examples.services

import com.github.chitralverma.schnapps.internal.RestService
import javax.ws.rs.Path
import javax.ws.rs.core.Response
import org.jboss.resteasy.spi.HttpRequest

@Path("ping")
class PingService extends RestService {

  override def get(request: HttpRequest): Response = {
    Response.ok.entity("""{"result":"pong"}""").build()
  }

}
