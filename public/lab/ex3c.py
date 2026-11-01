import pandas as pd
data=pd.Series(5,index=[100,200,300])
print(data)
ind=pd.Series({2:'a',1:'b',3:'c'})
print(ind)
ind1=pd.Series({2:'a',1:'b',3:'c'},index=[3,2])
print(ind1)
