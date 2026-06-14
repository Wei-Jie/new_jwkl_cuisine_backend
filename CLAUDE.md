# 小灶私廚開發與防錯五大鐵律

任何 AI 助理在修改本專案的程式碼前，必須逐一檢核並遵守以下規則，以確保與現有網站代碼 100% 向下相容，絕不引入舊 Bug：

## 1. 前後端變數命名對齊鐵律 (Naming Alignment)
- 前端從 API 讀取或寫入 `Menu` 與 `OrderItem` 資料時，必須在源頭進行「雙向相容映射」，同時賦予駝峰與底線欄位。
- 範例：
  ```js
  isStockManaged: m.isStockManaged !== undefined ? m.isStockManaged : m.is_stock_managed,
  is_stock_managed: m.isStockManaged !== undefined ? m.isStockManaged : m.is_stock_managed
  ```

## 2. 庫存統計「自我排除」鐵律 (Exclude Self in Reserved Calculations)
- 在後端（Java）或前端（React）計算某商品的「預約保留量」或「出貨可用庫存」時，若正處於編輯或出貨當下訂單的上下文中，**必須從總保留量中扣除/排除「當前這筆訂單自己」的明細數量**，避免重複計算導致庫存不足而被卡死。

## 3. 二階段 API 狀態前置傳遞鐵律 (Next Status Propagation)
- 前端編輯存檔時會先更新明細、再更新主狀態。
- 呼叫更新明細 API（`PUT /api/v1/orders/{orderId}/items`）時，前端必須以 Query 參數帶入即將更新的狀態 `nextStatus=...`。
- 後端在更新明細並檢查庫存時，必須將 `orderStatus` (舊狀態) 與 `nextStatus` (新狀態) 併入 `skipStockCheck` 判定中。若其中之一為「已出貨」或「已結單」，必須直接放行可用庫存檢查。

## 4. 秤重計價商品（牛腱/紅燒肉）特殊計價防呆 (Weight Item Protection)
- 任何涉及金額小計、總金額加總或編輯元件（金額/數量）輸入限制的程式碼，必須先偵測是否為秤重商品（例如價格包含 `*`、`重量`，或特定的 `product_id` ）。
- 秤重商品之小計計算公式必須為：`克數 * 單價比例`，不得誤套用一般商品的 `數量 * 單價` 公式。

## 5. RWD 表格與按鈕防溢出鐵律 (RWD Scrollbar Prevention)
- 修改 any Table 欄位時，必須限制電腦版表格總寬度在 `800px` 內以防溢出橫向捲軸。
- 在 `OrdersTab.jsx` 等表格的 `tbody` <td> 內，必須配置 `data-label` 屬性，以確保在手機版下能完美進行卡片化自適應。
