Viewed docker-compose.yml:1-15
Listed directory controller
Viewed WarehouseController.java:1-71
Viewed StockController.java:1-163

I've reviewed the API architecture in your controllers (`WarehouseController.java` and `StockController.java`). The application is split into two logical domains: **Warehouses** (the physical locations) and **Stock** (the actual inventory of products inside those locations).

Here is a comprehensive breakdown of your API endpoints, what they do, and how they should be used in a real-world scenario.

---

### 🏢 1. Warehouse Management API (`/api/v1/warehouses`)
These endpoints manage the physical or logical storage locations.

* **`POST /api/v1/warehouses`**
  * **Meaning:** Creates a new warehouse in the system.
  * **Usage:** Used when your company acquires a new storage facility or distribution center. Requires `ADMIN` or `MANAGER` roles.
* **`GET /api/v1/warehouses`**
  * **Meaning:** Lists all warehouses. 
  * **Usage:** Used by frontend dashboards to show a dropdown of available warehouses. You can pass `?activeOnly=true` to hide shut-down facilities.
* **`GET /api/v1/warehouses/{id}`**
  * **Meaning:** Fetches details of a specific warehouse.
* **`PUT /api/v1/warehouses/{id}`**
  * **Meaning:** Updates warehouse details (like changing the manager's name, or updating the address).
* **`DELETE /api/v1/warehouses/{id}`**
  * **Meaning:** "Soft deletes" a warehouse.
  * **Usage:** It doesn't actually delete the record from the database (to preserve historical data). Instead, it marks it as "inactive." Requires strict `ADMIN` privileges.

---

### 📦 2. Stock Management API (`/api/v1/stock`)
These endpoints handle the movement and tracking of individual products.

#### A. Initialization
* **`POST /api/v1/stock/warehouses/{w_id}/products/{p_id}/initialize`**
  * **Meaning:** Creates the very first stock record for a specific product inside a specific warehouse.
  * **Usage:** If you start selling a new product (e.g., "iPhone 15"), you must call this endpoint to establish its initial stock count, reorder points, and max capacity before you can adjust or transfer it.

#### B. Queries (Checking Inventory)
* **`GET /api/v1/stock/warehouses/{w_id}/products/{p_id}`**
  * **Meaning:** Get the exact stock level of one product in one warehouse.
* **`GET /api/v1/stock/warehouses/{w_id}`**
  * **Meaning:** Returns a list of *everything* stored inside a specific warehouse. Useful for auditing or printing inventory manifests.
* **`GET /api/v1/stock/products/{p_id}/total`**
  * **Meaning:** Aggregates the stock of a product across *all* warehouses. 
  * **Usage:** When a customer looks at a product on your website, this endpoint tells the frontend "We have 500 total in stock globally."
* **`GET /api/v1/stock/low-stock`**
  * **Meaning:** Returns a list of all products that have fallen below their configured `reorderPoint`.
  * **Usage:** Used by automated systems or managers to know what items need to be restocked immediately.

#### C. Mutations (Moving Inventory)
* **`PATCH /api/v1/stock/warehouses/{w_id}/adjust`**
  * **Meaning:** Manually adds or subtracts from the stock level.
  * **Usage:** Used during manual inventory checks. E.g., If a worker finds 5 damaged items, they send a negative quantity adjustment with a reason like `DAMAGED`. The system automatically blocks this if it results in negative stock.
* **`POST /api/v1/stock/transfer`**
  * **Meaning:** Moves stock from Warehouse A to Warehouse B.
  * **Usage:** It atomically subtracts from the source and adds to the destination. If the source doesn't have enough, the whole transaction safely rolls back to prevent lost stock.

#### D. Order Fulfillment (Microservice Integration)
* **`POST /.../reserve`** and **`POST /.../release`**
  * **Meaning:** Temporarily "reserves" stock without actually removing it.
  * **Usage:** When a customer clicks "Checkout", the Sale Order service calls this. The items are marked as "Reserved" so no one else can buy them while the payment is processing. If the payment fails, `release` is called to free up the stock again.
* **`POST /.../receive`**
  * **Meaning:** Receives new stock from a Purchase Order.
  * **Usage:** When a shipment arrives from a supplier, the Purchase Order service calls this to officially register the new stock into the warehouse.

---
**Summary of the Architecture:**
This is a highly robust, production-grade API. It uses **Optimistic Locking** to prevent race conditions (two people buying the last item at the exact same millisecond), and relies on **Kafka** to broadcast every single movement (like a digital paper trail) so other microservices (like a Reporting or Alert service) can react in real-time.