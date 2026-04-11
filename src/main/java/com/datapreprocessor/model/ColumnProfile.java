package com.datapreprocessor.model;

/**
 * Statistical profile computed for a single column by
 * {@link com.datapreprocessor.engine.DataCleaner#profileColumns(DataSet)}.
 */
public class ColumnProfile {

    public enum DataType { NUMERIC, TEXT, BOOLEAN, DATE, UNKNOWN }

    private final String   name;
    private final DataType dataType;
    private final int      totalCount;
    private final int      nullCount;
    private final int      uniqueCount;

    // Numeric statistics (NaN when column is non-numeric)
    private final double mean;
    private final double median;
    private final double stdDev;
    private final double min;
    private final double max;
    private final double q1;       // 25th percentile (used for IQR)
    private final double q3;       // 75th percentile (used for IQR)

    // Categorical
    private final String mostCommon;

    public ColumnProfile(Builder b) {
        this.name        = b.name;
        this.dataType    = b.dataType;
        this.totalCount  = b.totalCount;
        this.nullCount   = b.nullCount;
        this.uniqueCount = b.uniqueCount;
        this.mean        = b.mean;
        this.median      = b.median;
        this.stdDev      = b.stdDev;
        this.min         = b.min;
        this.max         = b.max;
        this.q1          = b.q1;
        this.q3          = b.q3;
        this.mostCommon  = b.mostCommon;
    }

    // -------------------------------------------------------------------------
    // Derived helpers
    // -------------------------------------------------------------------------

    public double getNullPercent() {
        return totalCount == 0 ? 0.0 : (nullCount * 100.0 / totalCount);
    }

    public double getIqr()        { return q3 - q1; }
    public double getLowerFence() { return q1 - 1.5 * getIqr(); }
    public double getUpperFence() { return q3 + 1.5 * getIqr(); }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String   getName()        { return name;        }
    public DataType getDataType()    { return dataType;    }
    public int      getTotalCount()  { return totalCount;  }
    public int      getNullCount()   { return nullCount;   }
    public int      getUniqueCount() { return uniqueCount; }
    public double   getMean()        { return mean;        }
    public double   getMedian()      { return median;      }
    public double   getStdDev()      { return stdDev;      }
    public double   getMin()         { return min;         }
    public double   getMax()         { return max;         }
    public double   getQ1()          { return q1;          }
    public double   getQ3()          { return q3;          }
    public String   getMostCommon()  { return mostCommon;  }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static class Builder {
        private String   name;
        private DataType dataType    = DataType.UNKNOWN;
        private int      totalCount  = 0;
        private int      nullCount   = 0;
        private int      uniqueCount = 0;
        private double   mean        = Double.NaN;
        private double   median      = Double.NaN;
        private double   stdDev      = Double.NaN;
        private double   min         = Double.NaN;
        private double   max         = Double.NaN;
        private double   q1          = Double.NaN;
        private double   q3          = Double.NaN;
        private String   mostCommon  = "";

        public Builder(String name)              { this.name        = name;       }
        public Builder dataType(DataType dt)     { this.dataType    = dt;         return this; }
        public Builder totalCount(int v)         { this.totalCount  = v;          return this; }
        public Builder nullCount(int v)          { this.nullCount   = v;          return this; }
        public Builder uniqueCount(int v)        { this.uniqueCount = v;          return this; }
        public Builder mean(double v)            { this.mean        = v;          return this; }
        public Builder median(double v)          { this.median      = v;          return this; }
        public Builder stdDev(double v)          { this.stdDev      = v;          return this; }
        public Builder min(double v)             { this.min         = v;          return this; }
        public Builder max(double v)             { this.max         = v;          return this; }
        public Builder q1(double v)              { this.q1          = v;          return this; }
        public Builder q3(double v)              { this.q3          = v;          return this; }
        public Builder mostCommon(String v)      { this.mostCommon  = v;          return this; }

        public ColumnProfile build()             { return new ColumnProfile(this); }
    }
}
