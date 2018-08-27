(ns lambdakube-example.core
  (:require [lambdakube.core :as lk]
            [clojure.java.io :as io]))

(defn redis-master [name labels res]
  (-> (lk/pod name labels)
      (lk/add-container :redis "k8s.gcr.io/redis:e2e"
                        {:resources res})
      (lk/deployment 1)
      (lk/expose-cluster-ip name (lk/port :redis 6379 6379))))

(defn redis-slave [name labels res replicas]
  (-> (lk/pod name labels)
      (lk/add-container :redis "gcr.io/google_samples/gb-redisslave:v1"
                        (-> {:resources res}
                            (lk/add-env {:GET_HOST_FROM :dns})))
      (lk/deployment replicas)
      (lk/expose-cluster-ip name (lk/port :redis 6379 6379))))

(defn map-resources [res-list]
  (->> (for [res res-list]
         [res (-> res io/resource slurp)])
       (into {})))

(defn module [$]
  (-> $
      (lk/rule :backend-master []
               #(redis-master :redis-master
                              {:app :redis
                               :role :master
                               :tier :backend}
                              {:requests {:cpu "100m"
                                          :memory "100Mi"}}))
      (lk/rule :backend-slave [:num-be-slaves]
               (fn [num-be-slaves]
                 (redis-slave :redis-slave
                              {:app :redis
                               :role :slave
                               :tier :backend}
                              {:requests {:cpu "100m"
                                          :memory "100Mi"}}
                              num-be-slaves)))
      (lk/rule :frontend [:backend-master :backend-slave :num-fe-replicas]
               (fn [master slave num-replicas]
                 (-> (lk/pod :frontend {:app :guesbook
                                        :tier :frontend})
                     (lk/add-container :php-redis "gcr.io/google-samples/gb-frontend:v4"
                                       (lk/add-env {}  {:GET_HOST_FROM :dns}))
                     (lk/add-files-to-container :php-redis :new-gb-fe-files "/var/www/html"
                                                (map-resources ["index.html" "controllers.js" "guestbook.php"]))
                     (lk/deployment num-replicas)
                     (lk/expose-node-port :frontend (lk/port :php-redis 80)))))))

(def config
  {:num-be-slaves 3
   :num-fe-replicas 3})

(defn -main []
  (-> (lk/injector)
      module
      (lk/get-deployable config)
      lk/to-yaml
      (lk/kube-apply (io/file "guestbook.yaml"))))


