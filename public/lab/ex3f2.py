import pandas as pd
data=[{'a':i,'b':2*i} for i in range(3)]
df=pd.DataFrame(data)
print(df)
data1=pd.DataFrame([{'a':1,'b':2},{'b':3,'c':4}])
print(data1)
