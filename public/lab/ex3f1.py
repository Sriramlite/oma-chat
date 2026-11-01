import pandas as pd
population_dict={'california':38332521,'texes':26448193,'New york':19651127,'florida':19552860,'illinois':12882135}
population=pd.Series(population_dict)
df=pd.DataFrame(population,columns=['population'])
print(df)
