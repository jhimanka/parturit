(ns user
  (:require [nextjournal.clerk :as clerk]))

(comment
  ;; Add tap sink. Assumes we are using a rich REPL like the Practicalli one that includes portal
  (require '[portal.api :as portal])
  (portal/open)
  (portal/tap)

  ;; start without file watcher, open browser when started
  (clerk/serve! {:browse? true})

  ;; start with file watcher for these sub-directory paths
  (clerk/serve! {:watch-paths ["notebooks"] :browse? true})

  ;; start with file watcher and a `show-filter-fn` function to watch
  ;; a subset of notebooks
  (clerk/serve! {:watch-paths ["notebooks" "src"] :show-filter-fn #(clojure.string/starts-with? % "notebooks")})

  (clerk/clear-cache!)

  ;; or call `clerk/show!` explicitly
  (clerk/show! "notebooks/parturit.clj")
  (clerk/show! "notebooks/barbers.clj")

  ;; TODO If you would like more details about how Clerk works, here's a
  ;; notebook with some implementation details.
  ;; (clerk/show! "notebooks/how_clerk_works.clj")

  ;; produce a static app
  (clerk/build-static-app! {:paths (mapv #(str "notebooks/" % ".clj")
                                         '[parturit barbers])})

  )
