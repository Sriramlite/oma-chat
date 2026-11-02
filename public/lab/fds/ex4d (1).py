import numpy as np
import pandas as pd
import seaborn as sns
import matplotlib.pyplot as plt

df = sns.load_dataset('iris')

print(df.info())

# Count plot
sns.countplot(x='species', data=df)
plt.show()

# Scatter plot - Sepal
sns.scatterplot(
    x='sepal_length',
    y='sepal_width',
    hue='species',
    data=df
)
plt.show()

# Scatter plot - Petal
sns.scatterplot(
    x='petal_length',
    y='petal_width',
    hue='species',
    data=df
)
plt.show()

# Correlation
print(df.corr(numeric_only=True, method='pearson'))

# Quartiles
Q1 = np.percentile(df['sepal_width'], 25, method='midpoint')
Q3 = np.percentile(df['sepal_width'], 75, method='midpoint')

IQR = Q3 - Q1

print("Q1:", Q1)
print("Q3:", Q3)
print("IQR:", IQR)

print("Old shape:", df.shape)

# Find outliers
upper = np.where(df['sepal_width'] >= (Q3 + 1.5 * IQR))
lower = np.where(df['sepal_width'] <= (Q1 - 1.5 * IQR))

print("Upper outliers:", upper)
print("Lower outliers:", lower)

# Remove outliers
df.drop(upper[0], inplace=True)
df.drop(lower[0], inplace=True)

print("New shape:", df.shape)

# Box plot
sns.boxplot(x='sepal_width', data=df)
plt.show()
