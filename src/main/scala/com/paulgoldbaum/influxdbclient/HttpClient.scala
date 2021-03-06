package com.paulgoldbaum.influxdbclient

import com.ning.http.client.Realm.{AuthScheme, RealmBuilder}
import com.ning.http.client._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.collection.JavaConverters._

protected object HttpClient {

  case class HttpResponse(code: Int, content: String)

  case class HttpJsonResponse(code: Int, content: Map[String, Object])

  class Config {
    private var builder = new AsyncHttpClientConfig.Builder

    def setConnectTimeout(timeout: Int) = {
      builder = builder.setConnectTimeout(timeout)
      this
    }

    def setReadTimeout(timeout: Int) = {
      builder = builder.setReadTimeout(timeout)
      this
    }

    def setRequestTimeout(timeout: Int) = {
      builder = builder.setRequestTimeout(timeout)
      this
    }

    protected[influxdbclient] def build() = builder.build()
  }
}

protected class HttpClient(val host: String,
                 val port: Int,
                 val username: String = null,
                 val password: String = null,
                 val clientConfig: HttpClient.Config = null)
{
  import HttpClient._

  implicit val ec = ExecutionContext.global
  val authenticationRealm = makeAuthenticationRealm()

  val client: AsyncHttpClient = if (clientConfig == null)
    new AsyncHttpClient()
  else
    new AsyncHttpClient(clientConfig.build())

  def get(url: String, params: Map[String, String] = Map()): Future[HttpResponse] = {

    val requestBuilder = client.prepareGet("http://%s:%d%s".format(host, port, url))
      .setRealm(authenticationRealm)
    requestBuilder.setQueryParams(params.map(p => new Param(p._1, p._2)).toList.asJava)

    val resultPromise = Promise[HttpResponse]()
    requestBuilder.execute(new ResponseHandler(resultPromise))
    resultPromise.future
  }

  def post(url: String, params: Map[String, String] = Map(), content: String): Future[HttpResponse] = {
    val requestBuilder = client.preparePost("http://%s:%d%s".format(host, port, url))
      .setRealm(authenticationRealm)
      .setBody(content)
    requestBuilder.setQueryParams(params.map(p => new Param(p._1, p._2)).toList.asJava)

    val resultPromise = Promise[HttpResponse]()
    requestBuilder.execute(new ResponseHandler(resultPromise))
    resultPromise.future
  }

  private def makeAuthenticationRealm() = username match {
    case null => null
    case _ => new RealmBuilder()
      .setPrincipal(username)
      .setPassword(password)
      .setUsePreemptiveAuth(true)
      .setScheme(AuthScheme.BASIC)
      .build()
  }

  private class ResponseHandler(promise: Promise[HttpResponse]) extends AsyncCompletionHandler[Response] {

    override def onCompleted(response: Response): Response = {
      if (response.getStatusCode >= 400)
        promise.failure(new HttpException("Server answered with error code " + response.getStatusCode, response.getStatusCode))
      else
        promise.success(new HttpResponse(response.getStatusCode, response.getResponseBody))
      response
    }

    override def onThrowable(throwable: Throwable) = {
      promise.failure(new HttpException("An error occurred during the request", -1, throwable))
    }
  }

}

class HttpException protected[influxdbclient]
(val str: String, val code: Int = -1, val throwable: Throwable = null) extends Exception(str, throwable) {}
