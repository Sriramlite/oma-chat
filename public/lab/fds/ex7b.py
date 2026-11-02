import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from sklearn import datasets, linear_model
from sklearn.metrics import mean_squared_error
diabetes = datasets.load_diabetes()
diabetes.keys()

df = pd.DataFrame(diabetes['data'], columns=diabetes['feature_names'])
x = df
y = diabetes['target']

from sklearn.model_selection import train_test_split

x_train, x_test, y_train, y_test = train_test_split(x, y, test_size=0.3, random_state=101)

from sklearn import linear_model
model = linear_model.LinearRegression() 
model.fit(x_train, y_train)
y_pre = model.predict(x_test) 

from sklearn.model_selection import cross_val_score
scores = cross_val_score(model, x, y, scoring="neg_mean_squared_error", cv=10)
rmse_scores = np.sqrt(-scores.mean())
print('Cross validation: ', rmse_scores)

from sklearn.metrics import r2_score
print('r^2:', r2_score(y_test, y_pre))
mse = mean_squared_error(y_test, y_pre)
rmse = np.sqrt(mse)
print('RMSE:', rmse)

print("Weights:", model.coef_)
print("\nIntercept", model.intercept_)
