(ns re-frame.fx
  (:require
    [re-frame.router      :as router]
    [re-frame.db          :refer [app-db]]
    [re-frame.interceptor :refer [->interceptor]]
    [re-frame.interop     :refer [set-timeout!]]
    [re-frame.events      :as events]
    ;; [re-frame.registrar   :refer [get-handler clear-handlers register-handler]]
    [re-frame.loggers     :refer [console]]
    [re-frame.trace :as trace :include-macros true]))


;; -- Registration ------------------------------------------------------------

;; (def kind :fx)

#_(defn reg-fx
  "Register the given effect `handler` for the given `id`.

  `id` is keyword, often namespaced.
  `handler` is a side-effecting function which takes a single argument and whose return
  value is ignored.

  Example Use
  -----------

  First, registration ... associate `:effect2` with a handler.

  (reg-fx
     :effect2
     (fn [value]
        ... do something side-effect-y))

  Then, later, if an event handler were to return this effects map ...

  {...
   :effect2  [1 2]}

   ... then the `handler` `fn` we registered previously, using `reg-fx`, will be
   called with an argument of `[1 2]`."
  [id handler]
  (register-handler kind id handler))

;; -- Interceptor -------------------------------------------------------------

(def do-fx
  "An interceptor whose `:after` actions the contents of `:effects`. As a result,
  this interceptor is Domino 3.

  This interceptor is silently added (by reg-event-db etc) to the front of
  interceptor chains for all events.

  For each key in `:effects` (a map), it calls the registered `effects handler`
  (see `reg-fx` for registration of effect handlers).

  So, if `:effects` was:
      {:dispatch  [:hello 42]
       :db        {...}
       :undo      \"set flag\"}

  it will call the registered effect handlers for each of the map's keys:
  `:dispatch`, `:undo` and `:db`. When calling each handler, provides the map
  value for that key - so in the example above the effect handler for :dispatch
  will be given one arg `[:hello 42]`.

  You cannot rely on the ordering in which effects are executed."
  (->interceptor
    :id :do-fx
    :after (fn do-fx-after
             [context]
             (trace/with-trace
               {:op-type :event/do-fx}
               (doseq [[effect-fn effect-value] (:effects context)]
                 (if effect-fn
                   (effect-fn effect-value)
                   (console :error "effect must not be nil. Ignoring.")))))))

;; -- Builtin Effect Handlers  ------------------------------------------------

;; :dispatch-later
;;
;; `dispatch` one or more events after given delays. Expects a collection
;; of maps with two keys:  :`ms` and `:dispatch`
;;
;; usage:
;;
;;    {:dispatch-later [{:ms 200 :dispatch [:event-id "param"]}    ;;  in 200ms do this: (dispatch [:event-id "param"])
;;                      {:ms 100 :dispatch [:also :this :in :100ms]}]}
;;
;; Note: nil entries in the collection are ignored which means events can be added
;; conditionally:
;;    {:dispatch-later [ (when (> 3 5) {:ms 200 :dispatch [:conditioned-out]})
;;                       {:ms 100 :dispatch [:another-one]}]}
;;
(defn dispatch-later [value]
  (doseq [{:keys [ms dispatch] :as effect} (remove nil? value)]
    (if (or (empty? dispatch) (not (number? ms)))
      (console :error "re-frame: ignoring bad :dispatch-later value:" effect)
      (set-timeout! #(router/dispatch dispatch) ms))))




;; :dispatch
;;
;; `dispatch` one event. Expects a single vector.
;;
;; usage:
;;   {:dispatch [:event-id "param"] }

(defn dispatch [value]
  (if-not (vector? value)
    (console :error "re-frame: ignoring bad :dispatch value. Expected a vector, but got:" value)
    (router/dispatch value)))


;; :dispatch-n
;;
;; `dispatch` more than one event. Expects a list or vector of events. Something for which
;; sequential? returns true.
;;
;; usage:
;;   {:dispatch-n (list [:do :all] [:three :of] [:these])}
;;
;; Note: nil events are ignored which means events can be added
;; conditionally:
;;    {:dispatch-n (list (when (> 3 5) [:conditioned-out])
;;                       [:another-one])}
;;
(defn dispatch-n [value]
  (if-not (sequential? value)
    (console :error "re-frame: ignoring bad :dispatch-n value. Expected a collection, got got:" value)
    (doseq [event (remove nil? value)] (router/dispatch event))))


;; :deregister-event-handler
;;
;; removes a previously registered event handler. Expects either a single id (
;; typically a namespaced keyword), or a seq of ids.
;;
;; usage:
;;   {:deregister-event-handler :my-id)}
;; or:
;;   {:deregister-event-handler [:one-id :another-id]}
;;
;; No longer needed
#_(defn deregister-event-handler [value]
  (let [clear-event (partial clear-handlers events/kind)]
    (if (sequential? value)
      (doseq [event value] (clear-event event))
      (clear-event value))))


;; :db
;;
;; reset! app-db with a new value. `value` is expected to be a map.
;;
;; usage:
;;   {:db  {:key1 value1 key2 value2}}
;;
(defn db [value]
  (if-not (identical? @app-db value)
    (reset! app-db value)))

