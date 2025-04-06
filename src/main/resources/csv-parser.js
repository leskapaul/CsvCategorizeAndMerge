// CSV Parser for transaction data
class TransactionParser {
  constructor() {
    this.transactions = [];
    this.dateRange = { min: null, max: null };
  }
  
  parseCSV(csvText) {
    // Reset data
    this.transactions = [];
    this.dateRange = { min: null, max: null };
    
    const lines = csvText.split('\n');
    let currentCategory = null;
    const headers = lines[0].split(',').map(h => h.trim());
    
    // Find column indices
    const dateIndex = headers.findIndex(h => h && h.toLowerCase() === 'date');
    const descIndex = headers.findIndex(h => h && h.toLowerCase() === 'description');
    const amountIndex = headers.findIndex(h => h && h.toLowerCase() === 'amount');
    
    if (dateIndex === -1 || descIndex === -1 || amountIndex === -1) {
      throw new Error('CSV must contain Date, Description, and Amount columns');
    }
    
    // Process lines
    for (let i = 1; i < lines.length; i++) {
      const line = lines[i].trim();
      if (!line) continue;
      
      // Check for category marker
      const categoryMatch = line.match(/^\[category=(.+)\]$/i);
      if (categoryMatch) {
        currentCategory = categoryMatch[1];
        continue;
      }
      
      // Process transaction
      const columns = line.split(',');
      if (columns.length > Math.max(dateIndex, descIndex, amountIndex)) {
        const date = columns[dateIndex] ? columns[dateIndex].trim() : '';
        if (!date) continue;
        
        const amountStr = columns[amountIndex] ? columns[amountIndex].trim() : '0';
        const amount = parseFloat(amountStr.replace(/[$,]/g, ''));
        if (isNaN(amount)) continue;
        
        const dateObj = this.parseDate(date);
        
        const transaction = {
          date: date,
          category: currentCategory || 'Uncategorized',
          description: columns[descIndex] ? columns[descIndex].trim() : '',
          amount: amount,
          dateObj: dateObj
        };
        
        // Track min/max dates
        if (dateObj) {
          if (!this.dateRange.min || dateObj < this.dateRange.min) {
            this.dateRange.min = dateObj;
          }
          if (!this.dateRange.max || dateObj > this.dateRange.max) {
            this.dateRange.max = dateObj;
          }
        }
        
        this.transactions.push(transaction);
      }
    }
    
    return this.transactions;
  }
  
  parseDate(dateStr) {
    const parts = dateStr.split('/');
    if (parts.length === 3) {
      return new Date(parts[2], parts[0]-1, parts[1]);
    }
    return null;
  }
  
  getDateRange() {
    return this.dateRange;
  }
}
