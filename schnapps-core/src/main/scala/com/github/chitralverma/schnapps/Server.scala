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

package com.github.chitralverma.schnapps

import java.io.File
import java.nio.file.Paths

import com.github.chitralverma.schnapps.config.Configuration
import com.github.chitralverma.schnapps.config.models._
import com.github.chitralverma.schnapps.enums.ProtocolEnums
import com.github.chitralverma.schnapps.internal.{Constants => Cnsnts, _}
import com.github.chitralverma.schnapps.internal.filters.CORSFilter
import com.github.chitralverma.schnapps.utils.Utils
import org.apache.dubbo.config._
import org.apache.dubbo.config.bootstrap.DubboBootstrap
import org.apache.shiro.SecurityUtils
import org.apache.shiro.env.BasicIniEnvironment
import org.apache.shiro.mgt.SecurityManager

import scala.collection.JavaConverters._

object Server extends Logging {

  private var _serverInstance: DubboBootstrap = _
  private var _securityManagerInstance: Option[SecurityManager] = _

  def getSecurityManager: Option[SecurityManager] = _securityManagerInstance

  def bootUp(configuration: Configuration): Unit = {
    if (_serverInstance == null) {
      _serverInstance = {
        val appConfig: ApplicationConfig = createAppConfig(configuration)
        val registryConfig: RegistryConfig = createServiceRegistryConfig(configuration)
        val protocolConfigs: Map[String, ProtocolConfig] = createProtocols(configuration)
        val serviceConfigs: Seq[ServiceConfig[_]] = createServices(configuration, protocolConfigs)

        DubboBootstrap
          .getInstance()
          .application(appConfig)
          .registry(registryConfig)
          .services(serviceConfigs.asJava)
      }

      _securityManagerInstance = configuration.serverConfig.shiroIniPath.map(path => {

        logInfo(s"Configuring Shiro Security using config at path '$path'")
        val securityManager: SecurityManager =
          new BasicIniEnvironment(path).getSecurityManager

        SecurityUtils.setSecurityManager(securityManager)
        securityManager
      })

      _serverInstance.start()

      if (_serverInstance.isReady && _serverInstance.isStarted) {
        logInfo("Server has started successfully and is ready for use. Use `Server.await()`.")
      } else {
        logError("Unable to start the server", throw new IllegalStateException())
      }
    } else logWarning("Server is already running")
  }

  def await(): Unit = {
    val instance: Option[DubboBootstrap] = Option(_serverInstance)
    assert(instance.isDefined, "Server has not been booted up yet. Use `Server.bootUp(...)`.")

    _serverInstance.await()
    logInfo("Server is now awaiting requests.")
  }

  private def createAppConfig(configuration: Configuration): ApplicationConfig = {
    val appInfo: AppInfoModel = configuration.appInfo

    val appConfig = new ApplicationConfig()
    appConfig.setName(appInfo.name)
    appConfig.setQosEnable(configuration.serverConfig.enableQOS)

    if (appInfo.organization.isDefined) {
      appConfig.setOrganization(appInfo.organization.get)
    }

    if (appInfo.owner.isDefined) {
      appConfig.setOwner(appInfo.owner.get)
    }

    if (appInfo.version.isDefined) {
      appConfig.setVersion(appInfo.version.get)
    }

    appConfig
  }

  private def createServiceRegistryConfig(configuration: Configuration): RegistryConfig = {
    val serviceRegistryConfigOpt: Option[ServiceRegistryConfigModel] =
      configuration.serverConfig.serviceRegistryConfig
    val registryConfig = new RegistryConfig()

    serviceRegistryConfigOpt match {
      case Some(srcm) =>
        registryConfig.setAddress(srcm.address)
        registryConfig.setUseAsConfigCenter(srcm.useAsConfigCenter)
        registryConfig.setUseAsMetadataCenter(srcm.useAsMetadataCenter)

        if (srcm.username.isDefined) {
          registryConfig.setUsername(srcm.username.get)
        }

        if (srcm.password.isDefined) {
          registryConfig.setPassword(srcm.password.get)
        }

        if (srcm.client.isDefined) {
          registryConfig.setClient(srcm.client.get)
        }

        if (srcm.timeoutMs.isDefined) {
          registryConfig.setTimeout(srcm.timeoutMs.get)
        }

        if (srcm.workingDir.isDefined) {
          assert(
            new File(srcm.workingDir.get).isDirectory,
            s"Provided value '${srcm.workingDir}' for is not a directory.")
          val registryCacheFile: String =
            Paths
              .get(srcm.workingDir.get, Cnsnts.RegistryCacheDir, Cnsnts.RegistryCacheFile)
              .toString

          registryConfig.setFile(registryCacheFile)
        }

      case None =>
        registryConfig.setAddress(RegistryConfig.NO_AVAILABLE)
        registryConfig.setUseAsConfigCenter(true)
        registryConfig.setUseAsMetadataCenter(true)
    }

    registryConfig.setGroup(
      serviceRegistryConfigOpt.flatMap(_.group).getOrElse(configuration.appInfo.name))

    logInfo(
      s"Connected to a '${registryConfig.getProtocol}' service registry at " +
        s"address '${registryConfig.getAddress}'")

    registryConfig
  }

  private def createProtocols(configuration: Configuration): Map[String, ProtocolConfig] = {
    configuration.serverConfig.protocolConfigs
      .map(p => {
        val protocolConfig = new ProtocolConfig()
        protocolConfig.setHost(configuration.serverConfig.host)
        protocolConfig.setPort(p.port)
        protocolConfig.setName(p.protocol.toString)
        protocolConfig.setContextpath(p.contextPath)
        protocolConfig.setExtension(
          s"${classOf[Extensions].getCanonicalName},${classOf[CORSFilter].getCanonicalName}")

        if (p.server.isDefined) {
          protocolConfig.setServer(p.server.get)
        } else if (p.protocol == ProtocolEnums.rest) {
          protocolConfig.setServer("netty") // todo move to enums
        }

        if (configuration.serverConfig.maxConnections.isDefined) {
          protocolConfig.setAccepts(configuration.serverConfig.maxConnections.get)
        }

        if (configuration.serverConfig.maxPayloadBytes.isDefined) {
          protocolConfig.setPayload(configuration.serverConfig.maxPayloadBytes.get)
        }

        if (configuration.serverConfig.ioThreads.isDefined) {
          protocolConfig.setIothreads(configuration.serverConfig.ioThreads.get)
        }

        if (configuration.serverConfig.threads.isDefined) {
          protocolConfig.setThreads(configuration.serverConfig.threads.get)
        }

        if (p.serialization.isDefined) {
          protocolConfig.setSerialization(p.serialization.get.toString)
        }

        logInfo(
          s"Created a '${p.protocol}' protocol with name '${p.name}' " +
            s"on host '${configuration.serverConfig.host}' port '${p.port}' and " +
            s"contextPath '${p.contextPath}'")
        p.name -> protocolConfig
      })
      .toMap
  }

  private def createServices(
      configuration: Configuration,
      protocolConfigs: Map[String, ProtocolConfig]): Seq[ServiceConfig[_]] = {
    if (configuration.services.isEmpty) {
      logInfo("No services were defined.")
    }

    val serviceConfigs: Seq[ServiceConfig[Any]] = configuration.services.map(definition => {
      protocolConfigs.get(definition.protocolName) match {
        case Some(protoConf) =>
          val serviceConfig = new ServiceConfig[Any]()
          serviceConfig.setProtocol(protoConf)
          serviceConfig.setRef(
            Utils
              .getInstance(definition.className, c => c.getDeclaredConstructor().newInstance()))

          serviceConfig.setInterface(
            definition.interfaceName.getOrElse(classOf[Service].getCanonicalName))
          serviceConfig.setId(
            s"${definition.protocolName}:${definition.className}:${definition.version}")

          if (definition.version.isDefined) {
            serviceConfig.setVersion(s"${definition.version.get}_${definition.className}")
          }

          logInfo(
            s"Created service with protocol name '${definition.protocolName}' " +
              s"class name '${definition.className}' and version '${definition.version.getOrElse(
                Cnsnts.EmptyString)}'")

          serviceConfig
        case None =>
          val exception = new IllegalArgumentException
          logError(
            s"No protocol was defined with name '${definition.protocolName}' " +
              s"for service with class name '${definition.protocolName}'",
            exception)
          throw exception
      }

    })

    serviceConfigs
  }

}
