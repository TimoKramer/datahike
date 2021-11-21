(ns build
  (:require [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as dd]))

(def lib 'timokramer/datahike)
(def version (format "0.4.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file (format "target/%s-%s.jar" (name lib) version))

(defn clean [_]
  (b/delete {:path "target"}))

(defn compile [_]
  (b/javac {:src-dirs ["java"]
            :class-dir class-dir
            :basis basis
            :javac-opts ["-source" "8" "-target" "8"]}))

(defn jar [_]
  (compile nil)
  (b/write-pom {:class-dir class-dir
                :src-pom "./template/pom.xml"
                :lib lib
                :version version
                :basis basis
                :src-dirs ["src"]})
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

(defn tag [_]
  (b/git-process {:git-args ["config" "--local" "user.email" "info@lambdaforge.io"]})
  (b/git-process {:git-args ["config" "--local" "user.name" "\"CircleCI Release-Pipeline\""]})
  (b/git-process {:git-args ["config" "--local" "credential.helper" '"!f()" "{" "sleep" "1;" "echo" "\"username=${GITHUB_USER}\";" "echo" "\"password=${GITHUB_TOKEN}\";" "};" "f'"]})
  (println (b/process {:command-args ["printenv"]
                       :out :capture}))
  (println (b/git-process {:git-args ["config" "--list" "--show-origin"]}))
  (b/git-process {:git-args ["tag" "-a" (format "v%s" version) "-m" "Released by CircleCI Pipeline"]})
  (b/git-process {:git-args ["remote" "add" "writeaccess" (format "https://github.com/%s.git" lib)]})
  (b/git-process {:git-args ["push" "--tags" "writeaccess"]}))

(comment
  (str lib))

(defn deploy [_]
  (println "Don't forget to set CLOJARS_USERNAME and CLOJARS_PASSWORD env vars.")
  (dd/deploy {:installer :remote :artifact jar-file
              :pom-file (b/pom-path {:lib lib :class-dir class-dir})}))

(defn install [_]
  (b/install {:basis (b/create-basis {})
              :lib lib
              :version version
              :jar-file jar-file
              :class-dir class-dir}))

(defn ci [_]
  (clean nil)
  (jar nil)
  (tag nil)
  (deploy nil))

(comment
  (b/pom-path {:lib lib :class-dir class-dir})
  (clean nil)
  (compile nil)
  (jar nil)
  (tag nil)
  (deploy nil)
  (install nil))
