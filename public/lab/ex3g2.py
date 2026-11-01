import pandas as pd
indA=pd.Index([1,3,5,7,9])
indB=pd.Index([2,3,5,7,11])
print(indA&indB)
print(indA|indB)
print(indA^indB)
