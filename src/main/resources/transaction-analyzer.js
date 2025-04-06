// Transaction data analyzer
class TransactionAnalyzer {
  constructor(transactions) {
    this.transactions = transactions || [];
  }
  
  setTransactions(transactions) {
    this.transactions = transactions;
  }
  
  getCategoryTotals() {
    const categoryTotals = {};
    
    this.transactions.forEach(t => {
      // Use the actual amount value
      categoryTotals[t.category] = (categoryTotals[t.category] || 0) + t.amount;
    });
    
    return categoryTotals;
  }
  
  getWeeklySpending() {
    const weeklyData = {};
    const allCategories = new Set();
    
    this.transactions.forEach(t => {
      if (!t.dateObj) return;
      allCategories.add(t.category);
      
      const year = t.dateObj.getFullYear();
      const weekNum = this.getWeekNumber(t.dateObj);
      const weekKey = `${year}-W${weekNum}`;
      
      if (!weeklyData[weekKey]) {
        weeklyData[weekKey] = {
          weekStart: new Date(t.dateObj),
          label: `W${weekNum} (${t.dateObj.getMonth()+1}/${t.dateObj.getDate()})`,
          categories: {}
        };
      }
      
      // Use the actual amount value
      weeklyData[weekKey].categories[t.category] = 
        (weeklyData[weekKey].categories[t.category] || 0) + t.amount;
    });
    
    return weeklyData;
  }
  
  getWeekNumber(date) {
    const firstDay = new Date(date.getFullYear(), 0, 1);
    return Math.ceil(((date - firstDay) / 86400000 + firstDay.getDay() + 1) / 7);
  }
  
  filterByDateRange(startDate, endDate) {
    if (!startDate && !endDate) return this.transactions;
    
    return this.transactions.filter(t => {
      if (!t.dateObj) return false;
      
      const isAfterStart = !startDate || t.dateObj >= startDate;
      const isBeforeEnd = !endDate || t.dateObj <= endDate;
      
      return isAfterStart && isBeforeEnd;
    });
  }
}
