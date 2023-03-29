(ns flow-storm.debugger.events-processor

  "Processing events the debugger receives from the runtime"

  (:require [flow-storm.debugger.ui.browser.screen :as browser-screen]
            [flow-storm.debugger.ui.taps.screen :as taps-screen]
            [flow-storm.debugger.state :as dbg-state]
            [flow-storm.debugger.ui.main :as ui-main]
            [flow-storm.debugger.ui.flows.screen :as flows-screen]
            [flow-storm.debugger.ui.docs.screen :as docs-screen]
            [flow-storm.debugger.config :refer [debug-mode]]
            [flow-storm.debugger.ui.utils :as ui-utils]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [flow-storm.utils :refer [log]]
            [flow-storm.debugger.ui.state-vars :as ui-vars]))

(defn- var-instrumented-event [{:keys [var-ns var-name]}]
  (ui-utils/run-later
   (browser-screen/add-to-var-instrumented-list var-ns var-name)
   (ui-main/select-main-tools-tab :browser)))

(defn- var-uninstrumented-event [{:keys [var-ns var-name]}]
  (ui-utils/run-later
   (browser-screen/remove-from-var-instrumented-list var-ns var-name)
   (ui-main/select-main-tools-tab :browser)))

(defn- namespace-instrumented-event [{:keys [ns-name]}]
  (ui-utils/run-later
   (browser-screen/add-to-namespace-instrumented-list [(browser-screen/make-inst-ns ns-name)])
   (ui-main/select-main-tools-tab :browser)))

(defn- namespace-uninstrumented-event [{:keys [ns-name]}]
  (ui-utils/run-later
   (browser-screen/remove-from-namespace-instrumented-list [(browser-screen/make-inst-ns ns-name)])
   (ui-main/select-main-tools-tab :browser)))

(defn- tap-event [{:keys [value]}]
  (ui-utils/run-later
   (taps-screen/add-tap-value value)
   (ui-main/select-main-tools-tab :taps)))

(defn- create-flow [{:keys [flow-id form-ns form timestamp]}]
  ;; lets clear the entire cache every time a flow gets created, just to be sure
  ;; we don't reuse old flows values on this flow
  (runtime-api/clear-api-cache rt-api)

  (dbg-state/create-flow flow-id form-ns form timestamp)
  (ui-utils/run-now (flows-screen/remove-flow flow-id))
  (ui-utils/run-now (flows-screen/create-empty-flow flow-id))
  (ui-utils/run-now (ui-main/select-main-tools-tab :flows)))

(defn- flow-created-event [flow-info]
  (create-flow flow-info))

(defn- thread-created-event [{:keys [flow-id]}]
  (when-not (dbg-state/get-flow flow-id)
    (create-flow {:flow-id flow-id}))
  (ui-utils/run-now
   (flows-screen/update-threads-list flow-id)
   #_(flows-screen/create-thread thread-info)))

(defn- task-result-event [{:keys [task-id result]}]
  (ui-vars/dispatch-task-event :result task-id result))

(defn- task-progress-event [{:keys [task-id progress]}]
  (ui-vars/dispatch-task-event :progress task-id progress))

(defn- heap-info-update-event [ev-args-map]
  (ui-main/update-heap-indicator ev-args-map))

(defn- show-doc-event [{:keys [var-symbol]}]
  (ui-utils/run-now
   (ui-main/select-main-tools-tab :docs)
   (docs-screen/show-doc var-symbol)))

(defn process-event [[ev-type ev-args-map]]
  (when debug-mode (log (format "Processing event: %s" [ev-type ev-args-map])))
  (case ev-type
    :var-instrumented (var-instrumented-event ev-args-map)
    :var-uninstrumented (var-uninstrumented-event ev-args-map)
    :namespace-instrumented (namespace-instrumented-event ev-args-map)
    :namespace-uninstrumented (namespace-uninstrumented-event ev-args-map)
    :flow-created (flow-created-event ev-args-map)
    :thread-created (thread-created-event ev-args-map)
    :tap (tap-event ev-args-map)
    :task-result (task-result-event ev-args-map)
    :task-progress (task-progress-event ev-args-map)
    :heap-info-update (heap-info-update-event ev-args-map)
    :show-doc (show-doc-event ev-args-map)))
