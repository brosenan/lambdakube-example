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
                      (lku/add-midje-container
                       :test
                       '[[org.clojure/clojure "1.8.0"]
                         [com.taoensso/carmine "2.18.1"]]
                       '[(ns main-test
                           (:require [midje.sweet :refer :all]
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
                          (Thread/sleep 1000)
                          (car/wcar slave-conn
                                    (car/with-replies
                                      (car/get "foo")) => "bar"))]))))
      (lkt/test :frontend-set-and-get
                {:num-be-slaves 1
                 :num-fe-replicas 1}
                [:frontend]
                (fn [frontend]
                  (-> (lk/pod :test {})
                      (lku/wait-for-service-port frontend :web)
                      (lku/add-midje-container
                       :test
                       '[[org.clojure/clojure "1.8.0"]
                         [clj-http "3.9.1"]]
                       '[(ns main-test
                           (:require [midje.sweet :refer :all]
                                     [clj-http.client :as client]
                                     [clojure.string :as str]))
                         (defn wget [query]
                           (let [base-url (System/getenv "FE_BASE_URL")
                                 resp (client/get (str base-url "?" (str/join "&" (for [[k v] query]
                                                                                    (str k "=" v)))))]
                             (when-not (= (:status resp) 200)
                               (throw (Exception. (str "Bad status" (:status resp) ": " (:body resp)))))
                             (:body resp)))
                         (fact
                          (wget {"cmd" "set"
                                 "key" "foo"
                                 "value" "bar"}) => "{\"message\": \"Updated\"}")
                         (fact
                          (wget {"cmd" "get"
                                 "key" "foo"}) => "{\"data\": \"bar\"}")])
                      (lk/update-container :test lk/add-env {"FE_BASE_URL" (str "http://"
                                                                                (-> frontend :hostname name)
                                                                                ":"
                                                                                (-> frontend :ports :web)
                                                                                "/guestbook.php")})
                      (update-in [:spec :initContainers 0] assoc :command
                                 ["sh" "-c" "while ! nc -z frontend 80; do sleep 1; done"]))))))

(deftest kubetests
  (is (= (-> (lk/injector)
             (lke/module)
             (lk/standard-descs)
             (test-module)
             (lkt/kube-tests "lk-ex")) "")))


