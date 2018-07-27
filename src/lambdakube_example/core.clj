(ns lambdakube-example.core
  (:require [lambdakube.core :as lk]
            [clojure.java.io :as io]))

(defn redis-master [name labels res]
  (-> (lk/pod name labels)
      (lk/add-container :master "k8s.gcr.io/redis:e2e"
                        {:resources res
                         :ports [{:containerPort 6379}]})
      (lk/deployment 1)
      (lk/expose {:ports [{:port 6379
                           :targetPort 6379}]})))

(defn redis-slave [name labels res replicas]
  (-> (lk/pod name labels)
      (lk/add-container :master "gcr.io/google_samples/gb-redisslave:v1"
                        (-> {:resources res
                             :ports [{:containerPort 6379}]}
                            (lk/add-env {:GET_HOST_FROM :dns})))
      (lk/deployment replicas)
      (lk/expose {:ports [{:port 6379}]})))

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
                                       (-> {:ports [{:containerPort 80}]}
                                           (lk/add-env {:GET_HOST_FROM :dns})))
                     (lk/deployment num-replicas)
                     (lk/expose {:ports [{:port 80}]
                                 :type :NodePort}))))))

(def config
  {:num-be-slaves 3
   :num-fe-replicas 3})

(defn -main []
  (-> (lk/injector config)
      module
      lk/get-deployable
      lk/to-yaml
      (lk/kube-apply (io/file "guestbook.yaml"))))


