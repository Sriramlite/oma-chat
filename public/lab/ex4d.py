import numpy as np
import pandas as pd
import seaborn as sns
import matplotlib.pyplot as plt
df = sns.load_dataset('iris')
print(df.info())
sns.countplot(x='Species',data=df)
plt.show()
sns-scatterplot(
    x='SepalLengthCm',
    y='SepalWidthCm',
    hue='Species',
    data=df)
plt.show()
sns-scatterplot(
    x='PetalLengthCm',
    y='PetalWidthCm',
    hue='Species',
    data=df)
plt.show()
print(df.corr(method='pearson'))

Q1=np.percentile(df['SepalWidth'],25,method='midpoint')
Q3=np.percentile(df['SepalWidth'],75,method='midpoint')
IQR=Q3-Q1
print("Old shape:",df.shape)
upper=np.where(df['sepalwidthcm']>=(Q3+1.5*IQR))
lower=np.where(df['sepalwidthcm']<=(Q1-1.5*IQR))
print("New shape:",df.shape)
sns.boxplot(x='sepalwidth',data=df)
plt.show()
