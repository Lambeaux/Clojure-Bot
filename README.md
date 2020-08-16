# Clojure Bot

A bare bones Rocket League bot written in Clojure for the JVM.

![](run/clojure-bot-nameplate.png)

## Some goals

- Provide an example Clojure bot that can be used as a reference in the RLBot community.
- Demonstrate how Clojure can enhance the bot workflow.
- Better understand the minimum requirements for iterating on a bot project.
- Better understand the minimum requirements for shipping a bot project in a tournament context.

## What's included vs not supported

This Clojure Bot example:
- Provides as much of the code in Clojure as possible.
- Provides several examples of how to do Java/Clojure inteorp.
- Supports local iteration of the bot through an embedded nREPL server in the bot process itself.
- Supports local iteration of the bot through bug fixes that inhibit development.

This bot deviates from the well known https://github.com/RLBot/RLBotJavaExample bot in several key ways.
- Leverages Maven for its build system instead of Gradle or [Leiningen](https://leiningen.org/), the latter
being one of the more popular options for Clojure projects.
- The bot does not yet support being used in competition, so no zip assembly is provided. Nor is there
any additional complexity for port resolution, locating the DLL, and any other challenges that come
with that territory. This will likely change in the near future.

### Useful commits

The commits have intentionally been structured so that key points in the bot's development
cycle can be browsed and reviewed to gather greater insight. Refer to the commit log and read
the commit descriptions. There are three key stages.
1. Bare-bones, executable Java bot that does nothing.
1. Bare-bones, executable Clojure bot that does nothing.
1. Basic Clojure bot that can chase the ball.

Referring to the incremental work makes it easier to discover which parts of the code are
essential to what particular function or piece of value.

## Clojure

Clojure is a language that gets out of the programmer's way and allows them to focus on the problem.

### What is it?

Clojure is a dynamic Lisp dialect that runs on the JVM. Writing Clojure code means writing data
structures. The Clojure compiler knows how to transform those data structures into JVM bytecode.

### Resources

There are several good resources for getting started with Clojure:
- https://www.braveclojure.com/introduction
- https://clojure.org/guides/getting_started

Some background on why Clojure was made and how it works:
- https://clojure.org/about/rationale
- https://clojure.org/reference/reader

### References

The pages to keep handy while writing Clojure:
- https://clojuredocs.org/quickref
- https://medium.com/@greg_63957/conj-cons-concat-oh-my-1398a2981eab
