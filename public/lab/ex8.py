import numpy as np
import matplotlib.pyplot as plt
from scipy import stats
data = np.random.normal(loc=50, scale=10, size=100)

z_scores = stats.zscore(data)
print("Z-scores:\n", z_scores)

plt.hist(data, bins=15, density=True, alpha=0.6, color='skyblue', edgecolor='black')

xmin, xmax = plt.xlim()
x = np.linspace(xmin, xmax, 100)
p = stats.norm.pdf(x, np.mean(data), np.std(data))
plt.plot(x, p, 'r', linewidth=2)
plt.title("Normal Distribution with Z-Scores")
plt.xlabel("Value")
plt.ylabel("Density")
plt.show()
