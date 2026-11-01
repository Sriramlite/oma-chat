import pandas as pd
import numpy as np
import statistics as st
import matplotlib.pyplot as plt
# Load the data
df = pd.read_csv("diabetes.csv")
print(df.shape)
print(df.info())
# Univariate Analysis of diabetes dataset
print('MEDIAN:\n', df.median())
print('MODE:\n', df.mode())
print('STANDARD DEVIATION:\n', df.std())
print('VARIANCE:\n', df.var())
print('SKEWNESS:\n', df.skew())
print('KURTOSIS:\n', df.kurtosis())
# Summary statistics
print(df.describe())
# Diabetes dataset in histogram
Data_X = df.copy(deep=True)
Data_X = Data_X.drop(['Outcome'], axis=1)
# Set figure size for histogram
plt.rcParams['figure.figsize'] = [40, 40]
# Plotting histogram of data
Data_X.hist(bins=40)
plt.show()
