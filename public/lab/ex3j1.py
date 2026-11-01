import pandas as pd
data=pd.Series([0.25,0.5,0.75,1.0],index=['a','b','c','d'])
print(data)
print(data['b'])
print('a' in data)
print(data.keys())
print(list(data.items()))
data['e']=1.25
print(data)
