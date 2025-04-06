// Chart visualization for transaction data
class ChartVisualizer {
  constructor() {
    this.isReady = false;
    this.onReadyCallback = null;
    
    google.charts.load('current', {'packages':['corechart', 'table']});
    google.charts.setOnLoadCallback(() => {
      this.isReady = true;
      if (this.onReadyCallback) {
        this.onReadyCallback();
      }
    });
  }
  
  onReady(callback) {
    if (this.isReady) {
      callback();
    } else {
      this.onReadyCallback = callback;
    }
  }
  
  drawPieChart(categoryTotals, elementId) {
    if (!categoryTotals || Object.keys(categoryTotals).length === 0) {
      document.getElementById(elementId).innerHTML = '<p class="no-data">No data available for the selected date range</p>';
      return;
    }
    
    try {
      const data = new google.visualization.DataTable();
      data.addColumn('string', 'Category');
      data.addColumn('number', 'Amount');
      
      Object.keys(categoryTotals).forEach(category => {
        // Use actual values - negative values will be expenses
        data.addRow([category, -categoryTotals[category]]);
      });
      
      const options = {
        title: 'Spending by Category',
        pieHole: 0.4,
        chartArea: {width: '80%', height: '80%'},
        legend: {position: 'right'},
        width: '100%',
        height: '100%',
        slices: {
          0: { color: '#4285F4' },
          1: { color: '#DB4437' },
          2: { color: '#F4B400' },
          3: { color: '#0F9D58' },
          4: { color: '#AB47BC' },
          5: { color: '#00ACC1' }
        }
      };
      
      const chart = new google.visualization.PieChart(document.getElementById(elementId));
      chart.draw(data, options);
    } catch (error) {
      console.error("Error drawing pie chart:", error);
      document.getElementById(elementId).innerHTML = '<p class="no-data">Error drawing chart: ' + error.message + '</p>';
    }
  }
  
  drawBarChart(categoryTotals, elementId) {
    if (!categoryTotals || Object.keys(categoryTotals).length === 0) {
      document.getElementById(elementId).innerHTML = '<p class="no-data">No data available for the selected date range</p>';
      return;
    }
    
    try {
      const data = new google.visualization.DataTable();
      data.addColumn('string', 'Category');
      data.addColumn('number', 'Amount');
      
      // Sort categories by amount (most negative first, as those are the biggest expenses)
      const sortedCategories = Object.keys(categoryTotals).sort((a, b) => 
        categoryTotals[a] - categoryTotals[b]
      );
      
      sortedCategories.forEach(category => {
        // Use actual values - negative values will be expenses
        data.addRow([category, -categoryTotals[category]]);
      });
      
      const options = {
        title: 'Spending by Category',
        chartArea: {width: '70%', height: '80%'},
        hAxis: {
          title: 'Amount',
          minValue: 0
        },
        vAxis: {
          title: 'Category'
        },
        colors: ['#4285F4'],
        width: '100%',
        height: '100%'
      };
      
      const chart = new google.visualization.BarChart(document.getElementById(elementId));
      chart.draw(data, options);
    } catch (error) {
      console.error("Error drawing bar chart:", error);
      document.getElementById(elementId).innerHTML = '<p class="no-data">Error drawing chart: ' + error.message + '</p>';
    }
  }
  
  drawLineChart(weeklyData, elementId) {
    const weeks = Object.values(weeklyData).sort((a, b) => a.weekStart - b.weekStart);
    
    if (weeks.length === 0) {
      document.getElementById(elementId).innerHTML = '<p class="no-data">No data available for the selected date range</p>';
      return;
    }
    
    try {
      const allCategories = new Set();
      weeks.forEach(week => {
        Object.keys(week.categories).forEach(cat => allCategories.add(cat));
      });
      
      const data = new google.visualization.DataTable();
      data.addColumn('string', 'Week');
      
      // Add columns for each category
      const categories = Array.from(allCategories);
      categories.forEach(cat => {
        data.addColumn('number', cat);
      });
      
      // Add rows
      weeks.forEach(week => {
        const row = [week.label];
        categories.forEach(cat => {
          // Use actual values - negative values will be expenses
          row.push(-week.categories[cat] || 0);
        });
        data.addRow(row);
      });
      
      const options = {
        title: 'Weekly Spending by Category',
        curveType: 'function',
        legend: { position: 'right' },
        chartArea: {width: '75%', height: '70%'},
        hAxis: { 
          title: 'Week',
          slantedText: true,
          slantedTextAngle: 30
        },
        vAxis: { title: 'Amount ($)' },
        width: '100%',
        height: '100%'
      };
      
      const chart = new google.visualization.LineChart(document.getElementById(elementId));
      chart.draw(data, options);
    } catch (error) {
      console.error("Error drawing line chart:", error);
      document.getElementById(elementId).innerHTML = '<p class="no-data">Error drawing chart: ' + error.message + '</p>';
    }
  }
  
  drawTransactionTable(transactions, elementId) {
    if (!transactions || transactions.length === 0) {
      document.getElementById(elementId).innerHTML = '<p class="no-data">No transactions available for the selected date range</p>';
      return;
    }
    
    try {
      const data = new google.visualization.DataTable();
      data.addColumn('string', 'Date');
      data.addColumn('string', 'Category');
      data.addColumn('string', 'Description');
      data.addColumn('number', 'Amount');
      
      transactions.forEach(function(transaction) {
        data.addRow([
          transaction.date || '',
          transaction.category || '',
          transaction.description || '',
          transaction.amount || 0
        ]);
      });
      
      const table = new google.visualization.Table(document.getElementById(elementId));
      table.draw(data, {
        showRowNumber: true, 
        width: '100%', 
        height: '300px',
        sortColumn: 0,  // Sort by date
        sortAscending: false  // Most recent first
      });
    } catch (error) {
      console.error("Error drawing table:", error);
      document.getElementById(elementId).innerHTML = '<p class="no-data">Error drawing table: ' + error.message + '</p>';
    }
  }
}
