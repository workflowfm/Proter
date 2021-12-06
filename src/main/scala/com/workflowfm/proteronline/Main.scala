package com.workflowfm.proteronline

import cats.effect.{ExitCode, IO, IOApp}

object Main extends IOApp {
  def run(args: List[String]) =
    ProteronlineServer.stream[IO].compile.drain.as(ExitCode.Success)
}
