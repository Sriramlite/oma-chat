import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import seaborn as sns

data = {
'X': np.random.randint(1, 100, 50),'Y': np.random.randint(1, 100, 50),
'Z': np.random.randint(1, 100, 50)
}
df = pd.DataFrame(data)

correlation_matrix = df.corr()
print("Correlation Coefficients:\n", correlation_matrix)

plt.figure(figsize=(8, 6))
sns.heatmap(correlation_matrix, annot=True, cmap='coolwarm')
plt.title("Correlation Coefficient Heatmap")
plt.show()

plt.scatter(df['X'], df['Y'], color='blue')
plt.xlabel('X')
plt.ylabel('Y')
plt.title('Scatter Plot between X and Y')
plt.show()

plt.scatter(df['X'], df['Z'], color='green')
plt.xlabel('X')
plt.ylabel('Z')
plt.title('Scatter Plot between X and Z')
plt.show()
