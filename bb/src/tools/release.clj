(ns tools.release
  (:require
   [babashka.fs :as fs]
   [babashka.http-client :as http]
   [babashka.process :as p]
   [borkdude.gh-release-artifact :as gh]
   [cheshire.core :as json]
   [clojure.string :as s]
   [clojure.tools.build.api :as b]
   [selmer.parser :refer [render]]
   [tools.build :as build]
   [tools.version :as version])
  (:import
   [clojure.lang ExceptionInfo]
   [java.nio.file FileAlreadyExistsException]))

(defn fib [a b]
  (lazy-seq (cons a (fib b (+ a b)))))

(defn retry-with-fib-backoff [retries exec-fn fail-test-fn]
  (loop [idle-times (take retries (fib 1 2))]
    (let [result (exec-fn)]
      (if (fail-test-fn result)
        (do (println "Returned: " result)
            (if-let [sleep-ms (first idle-times)]
              (do (println "Retrying with remaining back-off times (in s): " idle-times)
                  (Thread/sleep (* 1000 sleep-ms))
                  (recur (rest idle-times)))
              result))
        result))))

(defn try-release [file repo-config]
  (try (gh/release-artifact
        {:org (:org repo-config)
         :repo (:lib repo-config)
         :tag (version/string repo-config)
         :commit (version/current-commit)
         :file file
         :content-type "application/java-archive"
         :draft false})
       (catch ExceptionInfo e
         (assoc (ex-data e) :failure? true))))

(defn gh-release
  "Create a GitHub release and upload the library jar"
  [repo-config file]
  (println "Trying to release artifact" file "...")
  (let [_ (when-not (fs/exists? file)
            (println "Release file at" file "doesn't exist!")
            (System/exit 1))
        ret (retry-with-fib-backoff 10 #(try-release file repo-config) :failure?)]
    (if (:failure? ret)
      (do (println "GitHub release failed!")
          (System/exit 1))
      (println (:url ret)))))

(defn pod-release
  "Create a PR on babashka pod-registry"
  [repo-config]
  (let [version (version/string repo-config)
        branch-name (str "datahike-" version)
        home (System/getenv "HOME")
        ssh-key (System/getenv "GITHUB_SSH_KEY")
        github-token (System/getenv "GITHUB_TOKEN")]
    (println "Loading ssh-key")
    (if ssh-key
      (spit (str home "/.ssh/pod-registry-key") ssh-key)
      (do
        (println "Could not find ssh key")
        (System/exit 1)))
    (println (:err (b/process {:command-args ["ssh-add" (str home "/.ssh/pod-registry-key")] :out :capture :err :capture})))
    (println "Checking out pod-registry")
    (b/git-process {:git-args ["clone" "git@github.com:replikativ/pod-registry.git"] :dir "../" :capture :err})
    (b/git-process {:git-args ["checkout" "-b" branch-name] :dir "../pod-registry"})
    (b/git-process {:git-args ["config" "user.email" "info@lambdaforge.io"] :dir "../pod-registry"})
    (b/git-process {:git-args ["config" "user.name" "Datahike CI"] :dir "../pod-registry"})
    (println "Changing manifest")
    (let [manifest (slurp "../pod-registry/manifests/replikativ/datahike/0.6.1601/manifest.edn")]
      (try (fs/create-dir (str "../pod-registry/manifests/replikativ/datahike/" version))
        (catch FileAlreadyExistsException _
          (do
            (println "It seems there is already a release with that number")
            (System/exit 1))))
      (->> (s/replace manifest #"0\.6\.1601" version)
           (spit (str "../pod-registry/manifests/replikativ/datahike/" version "/manifest.edn"))))
    (println "Committing and pushing changes to fork")
    (b/git-process {:git-args "add manifests/replikativ/datahike" :dir "../pod-registry"})
    (b/git-process {:git-args ["commit" "-m" (str "Update Datahike pod to " version)] :dir "../pod-registry" :capture :err})
    (b/git-process {:git-args ["push" "origin" branch-name] :dir "../pod-registry"})
    (println "Creating PR on pod-registry")
    (let [url "https://api.github.com/repos/babashka/pod-registry/pulls"]
      (try
        (http/post url {:headers {"Accept" "application/vnd.github+json"
                                   "Authorization" (str "Bearer " github-token)
                                   "X-GitHub-Api-Version" "2022-11-28"
                                   "Content-Type" "application/json"}
                         :body (json/generate-string {:title (str "Update Datahike pod to " version)
                                                      :body "Automated update of Datahike pod"
                                                      :head (str "replikativ:" branch-name)
                                                      :base "master"})})
        (catch ExceptionInfo _
          (do
            (println "Failed creating PR on babashka/pod-registry")
            (System/exit 1)))))))

(defn zip-path [lib version target-dir zip-pattern]
  (let [platform (System/getenv "DTHK_PLATFORM")
        arch (System/getenv "DTHK_ARCH")]
    (if-not (and platform arch)
      (do (println "ERROR: Environment variables DTHK_PLATFORM and DTHK_ARCH need to be set")
          (System/exit 1))
      (str target-dir "/" (render zip-pattern {:platform platform
                                               :arch arch
                                               :lib lib
                                               :version version})))))

(defn zip-cli [config project]
  (let [{:keys [target-dir binary-name zip-pattern]} (-> config :release project)
        lib (:lib config)
        version (version/string config)
        binary-path (str target-dir "/" binary-name)]
    (if-not (fs/exists? binary-path)
      (do (println (str "ERROR: " binary-path " executable not found, please compile first."))
          (System/exit 1))
      (let [zip-name (zip-path lib version target-dir zip-pattern)]
        (p/shell "zip" zip-name binary-name)
        zip-name))))

(defn -main [config args]
  (let [cmd (first args)]
    (case cmd
      "jar" (gh-release config (build/jar-path config (-> config :build :clj)))
      "native-image" (->> (zip-cli config :native-cli)
                          (gh-release config))
      "pod" (pod-release config)
      (do (println "ERROR: Command not found: " cmd)
          (System/exit 1)))))
