(defproject org.immutant/immutant-build-assembly "1.0.3-SNAPSHOT"
  :parent [org.immutant/immutant-build _ :relative-path "../pom.xml"]
  :plugins [[lein-modules "0.1.1-SNAPSHOT"]
            [org.immutant/build-plugin "0.1.0-SNAPSHOT"]]
  :packaging "pom"

  :modules {:dirs ^:replace []})