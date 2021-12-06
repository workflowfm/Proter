package com.workflowfm.proteronline

import cats.effect.Sync
import cats.implicits._
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl

import cats.effect._
import cats.implicits._

import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.circe._
import org.http4s.EntityDecoder

object ProteronlineRoutes {

  def jokeRoutes[F[_]: Sync](J: Jokes[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}
    import dsl._
    HttpRoutes.of[F] {
      case GET -> Root / "joke" =>
        for {
          joke <- J.get
          resp <- Ok(joke)
        } yield resp
    }
  }

  def helloWorldRoutes[F[_]: Sync](H: HelloWorld[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}
    import dsl._
    HttpRoutes.of[F] {
      case GET -> Root / "hello" / name =>
        for {
          greeting <- H.hello(HelloWorld.Name(name))
          resp <- Ok(greeting)
        } yield resp
      case POST -> Root / "hello" /name =>
        for {
          greeting <- H.hello(HelloWorld.Name(name))
          resp <- Ok(greeting)
        } yield resp
    }
  }

  def apiRoutes[F[_]: Concurrent](): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}
    import dsl._
    implicit val reqDec: EntityDecoder[F, IRequest] = jsonOf[F, IRequest]
    val parse: JsonParser = new JsonParser()
    HttpRoutes.of[F] {
      case req @ POST -> Root / "API" =>
        for {
          request <- req.as[IRequest] //I dunno just get me the damn string out of the body!!!!
          resp <- Ok(parse.process(request).asJson)
        } yield resp
      }
  }
}