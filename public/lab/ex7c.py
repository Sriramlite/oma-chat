import  numpy as np
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

from sklearn.linear_model import LogisticRegression
model = LogisticRegression()
model.fit(x_train, y_train)
y_predicted = model.predict(x_test)
from sklearn.metrics import r2_score
print('r^2:', r2_score(y_test, y_predicted))

mse = mean_squared_error(y_test, y_predicted)
rmse = np.sqrt(mse)
print('RMSE:', rmse)
