(ns lambdakube-example.core-test
  (require [clojure.test :refer :all]
           [lambdakube-example.core :as lke]
           [lambdakube.core :as lk]
           [lambdakube.util :as lku]
           [lambdakube.testing :as lkt]))

(defn test-module [$]
  (-> $
      (lkt/test :redis-slave-configured
                {:num-be-slaves 1}
                [:backend-master :backend-slave]
                (fn [master slave]
                  (-> (lk/pod :test {})
                      (lku/wait-for-service-port master :redis)
                      (lku/wait-for-service-port slave :redis)
                      (lku/add-midje-container :test
                                               '[[org.clojure/clojure "1.8.0"]
                                                 [com.taoensso/carmine "2.18.1"]]
                                               '[(ns main-test
                                                   (require [midje.sweet :refer :all]
                                                            [taoensso.carmine :as car]))
                                                 (def master-conn {:pool {} :spec {:host "redis-master"
                                                                                   :port 6379}})
                                                 (def slave-conn {:pool {} :spec {:host "redis-slave"
                                                                                  :port 6379}})
                                                 (fact
                                                  (car/wcar master-conn
                                                            (car/with-replies
                                                              (car/set "foo" "bar")) => "OK")
                                                  ;; Wait for value to propagate to slave
                                                  (Thread/sleep 100)
                                                  (car/wcar slave-conn
                                                            (car/with-replies
                                                              (car/get "foo")) => "bar"))]))))))

(deftest kubetests
  (is (= (-> (lk/injector)
             (lke/module)
             (lk/standard-descs)
             (test-module)
             (lkt/kube-tests "lk-ex")) "")))


