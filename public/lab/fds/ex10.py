import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
from sklearn.linear_model import LinearRegression
from sklearn.metrics import r2_score

data = {
'X': [1, 2, 3, 4, 5, 6, 7, 8, 9, 10],
'Y': [2, 4, 5, 4, 5, 7, 8, 9, 10, 12]
}
df = pd.DataFrame(data)

X = df[['X']]
Y = df['Y']

model = LinearRegression()
model.fit(X, Y)

Y_pred = model.predict(X)
r2 = r2_score(Y, Y_pred)

print("Intercept:", model.intercept_)
print("Slope:", model.coef_[0])
print("R² Score:", r2)

plt.scatter(X, Y, color='blue', label='Data points')
plt.plot(X, Y_pred, color='red', label='Regression line')
plt.xlabel('X')
plt.ylabel('Y')
plt.title('Simple Linear Regression')
plt.legend()
plt.show()
