# Schema configuration

This guide covers how to configure field schemas in Nixiesearch, including the new numeric list field types.

## Basic schema structure

A Nixiesearch schema defines the structure of your index through field definitions. Each field specifies its data type and capabilities:

```yaml
schema:
  movies:
    fields:
      title:
        type: text
        search: true
        filter: true
        store: true
      year:
        type: int
        filter: true
        facet: true
        sort: true
      genres:
        type: text[]
        filter: true
        store: true
```

## Numeric field types

### Single-value numeric fields

```yaml
schema:
  products:
    fields:
      price:
        type: float
        filter: true
        sort: true
        facet: true
      quantity:
        type: int
        filter: true
        sort: true
      weight:
        type: double
        filter: true
        sort: true
      product_id:
        type: long
        filter: true
        required: true
```

### Numeric list fields

List/array variants allow storing multiple values per field:

```yaml
schema:
  ecommerce:
    fields:
      name:
        type: text
        search: true
      
      # Multiple ratings from different users
      ratings:
        type: int[]
        filter: true     # Enable range filtering
        store: true      # Store original values
        required: false  # Optional field
      
      # Historical price points  
      price_variants:
        type: float[]
        filter: true
        store: true
      
      # Product dimensions [length, width, height]
      dimensions:
        type: double[]
        store: true
        filter: false
      
      # Category relevance scores
      category_scores:
        type: long[]
        filter: true
        store: true
```

## Field configuration options

| Option | Description | Default | Available for |
|--------|-------------|---------|---------------|
| `type` | Field data type | required | All fields |
| `store` | Store original value | `true` | All fields |
| `filter` | Enable filtering | `false` | All fields |
| `sort` | Enable sorting | `false` | Single-value fields |
| `facet` | Enable faceting | `false` | Single-value fields |
| `search` | Enable text search | `false` | Text fields only |
| `required` | Field is mandatory | `false` | All fields |

## Complete example

```yaml
schema:
  product_catalog:
    fields:
      # Text fields
      title:
        type: text
        search: true
        filter: true
        store: true
        required: true
      
      description:
        type: text
        search: true
        store: true
      
      # Single numeric fields  
      price:
        type: float
        filter: true
        sort: true
        facet: true
        required: true
        
      stock:
        type: int
        filter: true
        sort: true
        
      # Numeric array fields
      user_ratings:
        type: int[]
        filter: true    # Can filter by rating ranges
        store: true
        
      size_options:
        type: float[]   # Available sizes: [6.5, 7.0, 7.5, 8.0]
        filter: true
        store: true
        
      # Other field types
      active:
        type: bool
        filter: true
        required: true
        
      created_date:
        type: datetime
        filter: true
        sort: true
```

This schema supports documents like:

```json
{
  "title": "Running Shoes",
  "description": "Comfortable athletic footwear",
  "price": 89.99,
  "stock": 25,
  "user_ratings": [5, 4, 5, 3, 4, 5, 2],
  "size_options": [6.0, 6.5, 7.0, 7.5, 8.0, 8.5, 9.0],
  "active": true,
  "created_date": "2024-01-15T10:30:00Z"
}
```