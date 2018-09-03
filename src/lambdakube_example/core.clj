(ns lambdakube-example.core
  (:require [lambdakube.core :as lk]
            [lambdakube.util :as lku]
            [clojure.java.io :as io]))

(defn redis-master [name labels res]
  (-> (lk/pod name labels)
      (lk/add-container :redis "redis:5.0-rc"
                        {:resources res})
      (lk/deployment 1)
      (lk/expose-cluster-ip name (lk/port :redis :redis 6379 6379))))

(defn redis-slave [name labels res master replicas]
  (-> (lk/pod name labels)
      (lk/add-container :redis "redis:5.0-rc"
                        {:resources res
                         :args ["--slaveof"
                                (:hostname master)
                                (-> master :ports :redis str)]})
      (lk/deployment replicas)
      (lk/expose-cluster-ip name (lk/port :redis :redis 6379 6379))))

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
      (lk/rule :backend-slave [:backend-master :num-be-slaves]
               (fn [backend-master num-be-slaves]
                 (redis-slave :redis-slave
                              {:app :redis
                               :role :slave
                               :tier :backend}
                              {:requests {:cpu "100m"
                                          :memory "100Mi"}}
                              backend-master
                              num-be-slaves)))
      (lk/rule :frontend [:backend-master :backend-slave :num-fe-replicas]
               (fn [master slave num-replicas]
                 ;; We start with an empty pod.
                 (-> (lk/pod :frontend {:app :guesbook
                                        :tier :frontend})
                     ;; We add a container, specifying a name, image and environments.
                     (lk/add-container :php-redis "gcr.io/google-samples/gb-frontend:v4"
                                       (lk/add-env {}  {:GET_HOST_FROM :dns}))
                     ;; We load three files from resources and mount them to the container
                     (lk/add-files-to-container :php-redis :new-gb-fe-files "/var/www/html"
                                                (map-resources ["index.html" "controllers.js" "guestbook.php"]))
                     ;; Wait for the master and slave to come up
                     (lku/wait-for-service-port master :redis)
                     (lku/wait-for-service-port slave :redis)
                     ;; Then we wrap the pod with a deployment, specifying the number of replicas.
                     (lk/deployment num-replicas)
                     ;; Finally, we expose port 80 using a NodePort service.
                     (lk/expose-node-port :frontend (lk/port :php-redis :web 80)))))))

(def config
  {:num-be-slaves 3
   :num-fe-replicas 3})

(defn -main []
  (-> (lk/injector)
      module
      lk/standard-descs
      (lk/get-deployable config)
      lk/to-yaml
      (lk/kube-apply (io/file "guestbook.yaml"))))


