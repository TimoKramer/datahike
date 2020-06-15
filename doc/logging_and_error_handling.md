# Logging and Error Handling

## Logging via the timbre logging library
Please see [Timbre on GitHub](https://github.com/ptaoussanis/timbre/) for details of the library. You can
use it via any of your preferred logging implementations on the JVM as well as in JS. Logging of an error
consists of the message as the first part and optionally of a map of details. These details mostly consist
of an `:error` key that describes where to search for your error and some information regarding the input
that created the error like `:value`, `:binding` or `:symbol`.

An example to configure logging for an application using Datahike:

```
(ns datahike-example-app.core
  (:require [taoensso.timbre :as log]
            [taoensso.timbre.appenders.3rd-party.rotor :as rotor]))

(log/merge-config! {:level :debug
    :appenders {:rotating (rotor/rotor-appender
                           {:path "/var/log/datahike-server.log"
                            :max-size (* 512 1024)
                            :backlog 10})}})
(log/infof "My first log in Datahike")
```

## Error Handling
Errors that are caught inside datahike create an `Execution error` that carries similar information like the
logging of these errors. An error consists of the message as the first part and optionally of a map of
details. These details mostly consist of an `:error` key that describes where to search for your error
and some information regarding the input that created the error like `:value`, `:binding` or `:symbol`.
