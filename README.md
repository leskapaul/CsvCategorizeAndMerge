# CsvCategorizeAndMerge

This is a Java-based utility for merging multiple CSV files into one, organizing rows into categories along the way.

## Example
Suppose you have multiple transaction exports from various banks and you want to consolidate them on a monthly basis.
Suppose each bank provides these transactions with different column names. Also suppose that you want to 
organize the transactions into categories based on regular expressions.

Suppose these are the transaction exports you are starting with:
#### Transactions From Bank A
```csv
date, transaction, category, amount
03/18/2024, Freepeople.com #2899, Shopping, -$69.95
03/12/2024, Rock farmers market, Groceries, $20.03
03/10/2024, Shoprite mple & hemlck, Groceries, $4.77
03/09/2024, The home depot #0983, Home, $12.06
03/09/2024, 99 ranch market #160, Groceries, $95.00
```
#### Transactions From Bank B
```csv
Date,Description,Amount,Running Bal.
03/04/2024,"COMPUTIL LLC RIDGEWOOD WATER Bill Payment","-109.80","11,785.72"
03/07/2024,"PUBLIC SERVICE DES:PSEG ID:XXXXX1111111 INDN:J DOE CO ID:XXXXX11111 PPD","-270.82","12,160.17"
03/19/2024,"VERIZON DES:PAYMENTREC ID:XXXXX99999999 INDN:J DOE CO ID:XXXXX99999 WEB","-24.99","11,166.92"
```
#### Input Configuration
The following configuration can be used to handle different column names and categorization.
```yaml
sortType: ASC
sortColumnName: Date
defaultCategoryName: Discretionary
columnNameToAliases:
  - Date
  - Description:
      - transaction
  - Amount
columnNameToCategoryConfig:
  - Description:
      - category: Groceries
        regexes:
          - "Shoprite.*"
          - "rock farmers market.*"
          - "99 ranch market.*"
      - category: Utilities
        regexes:
          - ".*ridgewood water.*"
          - "public service.*pseg.*"
      - category: Data
        regexes:
          - "verizon.*"
```
#### Program Output
This program outputs the following for the above configuration and CSV files. The transactions are organized into the 
the categories specified by configuration: Discretionary, Groceries, Utilities, and Data.
```csv
Date, Description, Amount
03/09/2024, The home depot #0983, $12.06
03/18/2024, Freepeople.com #2899, -$69.95

03/09/2024, 99 ranch market #160, $95.00
03/10/2024, Shoprite mple & hemlck, $4.77
03/12/2024, Rock farmers market, $20.03

03/04/2024, COMPUTIL LLC RIDGEWOOD WATER Bill Payment, -109.80
03/07/2024, PUBLIC SERVICE DES:PSEG ID:XXXXX1111111 INDN:J DOE CO ID:XXXXX11111 PPD, -270.82

03/19/2024, VERIZON DES:PAYMENTREC ID:XXXXX99999999 INDN:J DOE CO ID:XXXXX99999 WEB, -24.99 
```

